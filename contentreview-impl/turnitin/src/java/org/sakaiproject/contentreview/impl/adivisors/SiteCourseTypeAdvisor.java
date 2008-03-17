package org.sakaiproject.contentreview.impl.adivisors;

import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.site.api.Site;

public class SiteCourseTypeAdvisor implements ContentReviewSiteAdvisor {

	public boolean siteCanUseReviewService(Site site) {
		String type = site.getType();
		
		if (type != null ) {
			if (type.equals("course"))
				return true;
		}
		return false;
		
	}

}
