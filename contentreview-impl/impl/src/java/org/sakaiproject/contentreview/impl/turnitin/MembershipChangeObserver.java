package org.sakaiproject.contentreview.impl.turnitin;

import java.util.Date;
import java.util.Observable;
import java.util.Observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;
import org.sakaiproject.contentreview.model.ContentReviewRosterSyncItem;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;

public class MembershipChangeObserver implements Observer {
	private static final Log log = LogFactory.getLog(MembershipChangeObserver.class);

	public static final String MEMBERSHIP_EVENT = "site.upd.site.mbrshp";

	private EventTrackingService eventTrackingService;
	public void setEventTrackingService(EventTrackingService eventTrackingService) {
		this.eventTrackingService = eventTrackingService;
	}

	private TurnitinRosterSync turnitinRosterSync;
	public void setTurnitinRosterSync(TurnitinRosterSync turnitinRosterSync) {
		this.turnitinRosterSync = turnitinRosterSync;
	}

	private ContentReviewDao dao;
	public void setDao(ContentReviewDao dao) {
		this.dao = dao;
	}

	private ContentReviewService contentReviewService;
	public void setContentReviewService(ContentReviewService contentReviewService) {
		this.contentReviewService = contentReviewService;
	}

	private SiteService siteService;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	public void init() {
		eventTrackingService.addPriorityObserver(this);
	}

	@Override
	public void update(Observable o, Object arg) {
		if (arg instanceof Event) {
			Event event = (Event) arg;
			if (event.getEvent().equals(MEMBERSHIP_EVENT)) {
				Site site = null;
				try {
					site = siteService.getSite(event.getContext());
				} catch (IdUnusedException e) {
					log.error("Error observing Turnitin Membership update because we couldn't look up site: " + event.getContext(), e);
				}
				if (site != null && contentReviewService.isSiteAcceptable(site)) {
					ContentReviewRosterSyncItem syncitem = new ContentReviewRosterSyncItem();
					syncitem.setSiteId(event.getContext());
					syncitem.setDateQueued(new Date());
					syncitem.setStatus(ContentReviewRosterSyncItem.NOT_STARTED_STATUS);
					syncitem.setMessages("");
					dao.save(syncitem);
				}
				
				// TODO What about the situation where a site has been using
				// turnitin, but then it's turned off and might be turned on
				// again? We'd still want to adjust the membership then...
				
				//log.info("TurnitinRoster: Handling membership event.");
				// TODO Check and see if the site is even registered to use
				// turnitin, they we don't have to call TII for every site
				//turnitinRosterSync.syncSiteWithTurnitin(event.getContext());
			}
		}
	}

}
