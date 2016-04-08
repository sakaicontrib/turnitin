package org.sakaiproject.contentreview.impl.advisors;

import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;

public class GlobalPropertyAdvisor implements ContentReviewSiteAdvisor {

	private String tiiProperty;
	public void setTiiProperty(String p){
		tiiProperty = p;
	}
	
	private String tiiLTIProperty;
	public void setTiiLTIProperty(String p){
		tiiLTIProperty = p;
	}
	
	private String tiiDirectSubmissionProperty;
	public void setTiiDirectSubmissionProperty(String p){
		tiiDirectSubmissionProperty = p;
	}
	
	private ServerConfigurationService serverConfigurationService;
	public void setServerConfigurationService(ServerConfigurationService s){
		serverConfigurationService = s;
	}
	
	public boolean siteCanUseReviewService(Site site) {
		return serverConfigurationService.getBoolean(tiiProperty, false);
	}
	
	public boolean siteCanUseLTIReviewService(Site site) {
		return serverConfigurationService.getBoolean(tiiLTIProperty, false);
	}
	
	public boolean siteCanUseLTIDirectSubmission(Site site){
		return serverConfigurationService.getBoolean(tiiDirectSubmissionProperty, false);
	}

}
