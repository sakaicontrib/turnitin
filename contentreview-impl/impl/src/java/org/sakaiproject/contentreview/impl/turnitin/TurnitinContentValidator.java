package org.sakaiproject.contentreview.impl.turnitin;

import java.io.IOException;

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

	private int TII_MAX_FILE_SIZE;
	
	private ServerConfigurationService serverConfigurationService; 
	public void setServerConfigurationService (ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}
	
	public void init() {
		TII_MAX_FILE_SIZE = serverConfigurationService.getInt("turnitin.maxFileSize",10995116);
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
			if (extension.equals(".doc") || extension.equals(".docx") || ".rtf".equals(extension)) {
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
		//for now we accept all content
		// TODO: Check against content types accepted by Turnitin
		/*
		 * Turnitin currently accepts the following file types for submission: MS Word (.doc), WordPerfect (.wpd), PostScript (.eps), Portable Document Format (.pdf), HTML (.htm), Rich Text (.rtf) and Plain Text (.txt)
		 * text/plain
		 * text/html
		 * application/msword
		 * application/msword
		 * application/postscript
		 */

		String mime = resource.getContentType();
		log.debug("Got a content type of " + mime);

		Boolean fileTypeOk = false;
		if ((mime.equals("text/plain") || mime.equals("text/html") || mime.equals("application/msword") || 
				mime.equals("application/postscript") || mime.equals("application/pdf") || mime.equals("text/rtf")) ) {
			fileTypeOk =  true;
			log.debug("FileType matches a known mime");
		}

		//as mime's can be tricky check the extensions
		if (!fileTypeOk) {
			ResourceProperties resourceProperties = resource.getProperties();
			String fileName = resourceProperties.getProperty(resourceProperties.getNamePropDisplayName());
			if (fileName.indexOf(".")>0) {

				String extension = fileName.substring(fileName.lastIndexOf("."));
				log.debug("file has an extension of " + extension);
				if (extension.equals(".doc") || extension.equals(".wpd") || extension.equals(".eps") 
						||  extension.equals(".txt") || extension.equals(".htm") || extension.equals(".html") 
						|| extension.equals(".pdf") || extension.equals(".docx") || ".rtf".equals(extension))
					fileTypeOk = true;

			} else {
				//we don't know what this is so lets submit it anyway
				fileTypeOk = true;
			}
		}

		if (!fileTypeOk) {
			return false;
		}

		//TODO: if file is too big reject here 10.48576 MB

		if (resource.getContentLength() > TII_MAX_FILE_SIZE) {
			log.debug("File is too big: " + resource.getContentLength());
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
