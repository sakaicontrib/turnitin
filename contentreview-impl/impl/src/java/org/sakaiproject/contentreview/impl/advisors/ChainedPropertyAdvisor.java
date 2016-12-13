package org.sakaiproject.contentreview.impl.advisors;

import java.util.Date;
import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.site.api.Site;

import java.util.List;

/**
 * This checks all the advisors in the list and if any say <code>true</code> then we return <code>true</code>.
 * Basically is does a OR on all the advisors.
 */
public class ChainedPropertyAdvisor implements ContentReviewSiteAdvisor {

    private List<ContentReviewSiteAdvisor> advisors;

    public void setAdvisors(List<ContentReviewSiteAdvisor> advisors) {
        this.advisors = advisors;
    }

    @Override
    public boolean siteCanUseReviewService(Site site) {
        for(ContentReviewSiteAdvisor advisor : advisors) {
            if (advisor.siteCanUseReviewService(site)){
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean siteCanUseLTIReviewService(Site site) {
        for(ContentReviewSiteAdvisor advisor: advisors) {
            if (advisor.siteCanUseLTIReviewService(site)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean siteCanUseLTIReviewServiceForAssignment(Site site, Date asnCreationDate) {
        for(ContentReviewSiteAdvisor advisor: advisors) {
            if (advisor.siteCanUseLTIReviewServiceForAssignment(site, asnCreationDate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean siteCanUseLTIDirectSubmission(Site site) {
        for(ContentReviewSiteAdvisor advisor: advisors) {
            if (advisor.siteCanUseLTIDirectSubmission(site)) {
                return true;
            }
        }
        return false;
    }
}
