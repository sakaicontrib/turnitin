package org.sakaiproject.contentreview.impl.adivisors;

import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.site.api.Site;

public class defaultSiteAdvisor implements ContentReviewSiteAdvisor {

	public boolean siteCanUseReviewService(Site site) {
		return true;
	}

}
