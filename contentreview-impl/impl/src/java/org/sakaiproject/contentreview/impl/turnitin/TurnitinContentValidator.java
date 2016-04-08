/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/turnitin/trunk/contentreview-impl/impl/src/java/org/sakaiproject/contentreview/impl/turnitin/TurnitinReviewServiceImpl.java $
 * $Id: TurnitinReviewServiceImpl.java 69345 2010-07-22 08:11:44Z david.horwitz@uct.ac.za $
 ***********************************************************************************
 *
 * Copyright (c) 2006 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.contentreview.impl.turnitin;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.ServerOverloadException;

/**
 * This class contains the implementation of 
 * {@link ContentReviewService.isAcceptableContent}. This includes other
 * utility logic for checking the length and correctness of Word Documents and
 * other things.
 * 
 * @author sgithens
 *
 */
public class TurnitinContentValidator {
	private static final Log log = LogFactory.getLog(TurnitinContentValidator.class);

	private int tii_Max_Fil_Size;
	/**
	 * Default max allowed filesize - should match turnitins own setting (surrently 20Mb)
	 */
	private static int TII_DEFAULT_MAX_FILE_SIZE = 41943040;
	
	ContentReviewService contentReviewService = null;
	public void setContentReviewService(ContentReviewService contentReviewService)
	{
		this.contentReviewService = contentReviewService;
	}

	private ServerConfigurationService serverConfigurationService; 
	public void setServerConfigurationService (ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}
	
	public void init() {
		tii_Max_Fil_Size = serverConfigurationService.getInt("turnitin.maxFileSize", TII_DEFAULT_MAX_FILE_SIZE);
	}
	
	private boolean isMsWordDoc(ContentResource resource) {
		String mime = resource.getContentType();
		log.debug("Got a content type of " + mime);


		if (mime.equals("application/msword")) {
			log.debug("FileType matches a known mime");
			return true;
		}

		ResourceProperties resourceProperties = resource.getProperties();
		String fileName = resourceProperties.getProperty(resourceProperties.getNamePropDisplayName());
		if (fileName.contains(".")) {
			String extension = fileName.substring(fileName.lastIndexOf("."));
			log.debug("file has an extension of " + extension);
			if (extension.equalsIgnoreCase(".doc") || extension.equalsIgnoreCase(".docx") || ".rtf".equalsIgnoreCase(extension)) {
				return true;
			}
		} else {
			//if the file has no extension we assume its a doc
			return true;
		}

		return false;
	}
	
	private int wordDocLength(ContentResource resource) {
		if (!serverConfigurationService.getBoolean("tii.checkWordLength", false))
			return 100;

		try {
			POIFSFileSystem pfs = new POIFSFileSystem(resource.streamContent());
			HWPFDocument doc = new HWPFDocument(pfs);
			SummaryInformation dsi = doc.getSummaryInformation();
			int count = dsi.getWordCount();
			log.debug("got a count of " + count);
			//if this == 0 then its likely that something went wrong -poi couldn't read it
			if (count == 0)
				return 100;
			return count;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServerOverloadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//in case we can't read this lets err on the side of caution
		return 100;
	}
	
	public boolean isAcceptableContent(ContentResource resource) {
		// Check against content types accepted by Turnitin
		/*
		 * Turnitin currently accepts the following file types for submission: MS Word (.doc), WordPerfect (.wpd), PostScript (.eps), Portable Document Format (.pdf), HTML (.htm), Rich Text (.rtf) and Plain Text (.txt)
		 * text/plain
		 * text/html
		 * application/msword
		 * application/msword
		 * application/postscript
		 * UPDATED 15/03/2016 https://guides.turnitin.com/01_Manuals_and_Guides/Student/Student_User_Manual/09_Submitting_a_Paper
		 */

		String mime = resource.getContentType();
		log.debug("Got a content type of " + mime);

		// TII-157	--bbailla2
		Map<String, SortedSet<String>> acceptableExtensionsToMimeTypes = contentReviewService.getAcceptableExtensionsToMimeTypes();
		Set<String> acceptableMimeTypes = new HashSet<String>();
		for (SortedSet<String> mimeTypes : acceptableExtensionsToMimeTypes.values())
		{
			acceptableMimeTypes.addAll(mimeTypes);
		}

		Boolean fileTypeOk = false;
		if (acceptableMimeTypes.contains(mime))
		{
			fileTypeOk =  true;
			log.debug("FileType matches a known mime");
		}
		else
		{
			log.debug("FileType doesn't match a known mime");
			//TODO: return false here if we're confident that CRS.getAcceptableExtensionsToMimeTypes() gets us all of TII's acceptable mime types
		}

		//as mime's can be tricky check the extensions
		if (!fileTypeOk) {
			ResourceProperties resourceProperties = resource.getProperties();
			String fileName = resourceProperties.getProperty(resourceProperties.getNamePropDisplayName());
			if (fileName.indexOf(".")>0) {

				String extension = fileName.substring(fileName.lastIndexOf("."));
				log.debug("file has an extension of " + extension);
				Set<String> extensions = acceptableExtensionsToMimeTypes.keySet();
				if (extensions.contains(extension))
				{
					fileTypeOk = true;
				}
				else
				{
					// Neither the mime type nor the file extension are accepted
					return false;
				}

			} else {
				// No extension is not accepted
				// TODO: Make this configurable
				return false;
			}
		}

        // for files like .png we'd like to get a status code from TII so we can display an error message
        // other than "An unknown error occurred"
		if (!fileTypeOk) {
			return false;
		} else {
			return true;
		}
	}

	public boolean isAcceptableSize(ContentResource resource) {

		if (resource.getContentLength() > tii_Max_Fil_Size) {
			log.warn("File " + resource.getId() + " is too big: " + resource.getContentLength());
			return false;
		}

		//TII-93 content length must be > o
		if (resource.getContentLength() == 0) {
			log.warn("File " + resource.getId() + " is Ob");
			return false;
		}
		
		//if this is a msword type file we can check the legth
		if (isMsWordDoc(resource)) {
			if (wordDocLength(resource) < 20) {
				return false;
			}
		}

		return true;
	}
}
