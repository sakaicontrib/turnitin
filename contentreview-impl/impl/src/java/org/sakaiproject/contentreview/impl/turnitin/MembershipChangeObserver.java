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
import org.sakaiproject.genericdao.api.search.Restriction;
import org.sakaiproject.genericdao.api.search.Search;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;

/**
 * This Observer will watch for site.upd.site.mbrshp events. When these happen
 * and the site is enabled for use with Turnitin, an entry will be added to a
 * queue table for a quartz job or other script to run through and sync.
 * 
 * @author sgithens
 *
 */
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
		eventTrackingService.addLocalObserver(this);
	}

	
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
					Restriction notFinished = new Restriction("status", ContentReviewRosterSyncItem.FINISHED_STATUS, Restriction.NOT_EQUALS);
					Restriction siteIdEquals = new Restriction("siteId", event.getContext(), Restriction.EQUALS);
					Search search = new Search(new Restriction[] {notFinished,siteIdEquals});
					ContentReviewRosterSyncItem syncitem = 
						dao.findOneBySearch(ContentReviewRosterSyncItem.class, search);
					if (syncitem == null) {
						log.info("Adding site to Turnitin Roster Sync Queue: " + event.getContext());
						syncitem = new ContentReviewRosterSyncItem();
						syncitem.setSiteId(event.getContext());
						syncitem.setDateQueued(new Date());
						syncitem.setStatus(ContentReviewRosterSyncItem.NOT_STARTED_STATUS);
						syncitem.setMessages("");
					}
					else {
						log.info("Updating existing site in Turnitin Roster Sync Queue: " + event.getContext());
						StringBuilder sb = syncitem.getMessages() == null ? new StringBuilder() 
									: new StringBuilder(syncitem.getMessages());
						sb.append("\n"+(new Date()).toLocaleString()+"Additional Sakai Membership change triggered.");
						syncitem.setMessages(sb.toString());
					}
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
