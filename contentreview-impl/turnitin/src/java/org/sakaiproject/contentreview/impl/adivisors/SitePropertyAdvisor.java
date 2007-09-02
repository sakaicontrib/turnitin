package org.sakaiproject.contentreview.impl.adivisors;

import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;

public class SitePropertyAdvisor implements ContentReviewSiteAdvisor {

	private String siteProperty;
	public void setSiteProperty(String p){
		siteProperty = p;
	}
	
	
	public boolean siteCanUseReviewService(Site site) {
		
		ResourceProperties properties = site.getProperties();
		
		String prop = (String) properties.get(siteProperty);
		if (prop != null) {
			return Boolean.valueOf(prop).booleanValue();
		}
		
		return false;
	}

}
