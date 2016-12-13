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
package org.sakaiproject.contentreview.impl.advisors;

import java.util.Date;
import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;

public class SitePropertyAdvisor implements ContentReviewSiteAdvisor {

	private String siteProperty;
	public void setSiteProperty(String p){
		siteProperty = p;
	}
	
	private String siteLTIProperty;
	public void setSiteLTIProperty(String p){
		siteLTIProperty = p;
	}
	
	private String siteDirectSubmissionProperty;
	public void setSiteDirectSubmissionProperty(String p){
		siteDirectSubmissionProperty = p;
	}
	
	public boolean siteCanUseReviewService(Site site) {		
		ResourceProperties properties = site.getProperties();		
		String prop = properties.getProperty(siteProperty);
		return Boolean.valueOf(prop).booleanValue();
	}
	
	public boolean siteCanUseLTIReviewService(Site site) {
		ResourceProperties properties = site.getProperties();		
		String prop = properties.getProperty(siteLTIProperty);
		return Boolean.valueOf(prop).booleanValue();
	}
	
	@Override
	public boolean siteCanUseLTIReviewServiceForAssignment(Site site, Date assignmentCreationDate)
	{
		return siteCanUseLTIReviewService(site);
	}
	
	public boolean siteCanUseLTIDirectSubmission(Site site){
		ResourceProperties properties = site.getProperties();		
		String prop = properties.getProperty(siteDirectSubmissionProperty);
		return Boolean.valueOf(prop).booleanValue();
	}

}
