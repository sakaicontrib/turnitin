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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.sakaiproject.contentreview.model.ContentReviewRosterSyncItem;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.genericdao.api.search.Order;
import org.sakaiproject.genericdao.api.search.Restriction;
import org.sakaiproject.genericdao.api.search.Search;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.turnitin.util.TurnitinAPIUtil;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class contains functionality to sync the membership between a Turnitin
 * Course and a Sakai Site.
 * 
 * @author sgithens
 *
 */
public class TurnitinRosterSync { 

	private static final Log log = LogFactory.getLog(TurnitinRosterSync.class);

	final static long LOCK_PERIOD = 12000000;

	private TurnitinReviewServiceImpl turnitinReviewServiceImpl;
	public void setTurnitinReviewServiceImpl(TurnitinReviewServiceImpl turnitinReviewServiceImpl) {
		this.turnitinReviewServiceImpl = turnitinReviewServiceImpl;
	}

	private SiteService siteService;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

	private TurnitinAccountConnection turnitinConn;
	public void setTurnitinConn(TurnitinAccountConnection turnitinConn) {
		this.turnitinConn = turnitinConn;
	}

	private ContentReviewDao dao;
	public void setDao(ContentReviewDao dao) {
		this.dao = dao;
	}

	private ServerConfigurationService serverConfigurationService;
	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	public void init() {

	}

	/**
	 * This method takes a Sakai Site ID and returns the xml document from 
	 * Turnitin that lists all the instructors and students for that Turnitin 
	 * course.
	 * 
	 * @param sakaiSiteID
	 * @return
	 */
	public Document getEnrollmentDocument(String sakaiSiteID) {
		Map instinfo = turnitinConn.getInstructorInfo(sakaiSiteID);

		Map params = TurnitinAPIUtil.packMap(null,
				"fid","19",
				"fcmd","5",
				"utp","2",
				"ctl",sakaiSiteID,
				"cid",sakaiSiteID,
				"src","9",
				"encrypt","0"
		);
		params.putAll(instinfo);

		Document togo = null;
		try {
			togo = turnitinConn.callTurnitinWDefaultsReturnDocument(params);
		} catch (SubmissionException e) {
			log.error("Error getting enrollment document for sakai site: " 
					+ sakaiSiteID, e);
		} catch (TransientSubmissionException e) {
			log.error("Error getting enrollment document for sakai site: " 
					+ sakaiSiteID, e);
		}
		return togo;
	}

	/**
	 * This will make an API call to Turnitit to fetch the list of instructors
	 * and students for the site.  Remember that in Turnitin, a user can be 
	 * <strong>both</strong> a student and an instructor.
	 * 
	 * @param sakaiSiteID
	 * @return An Map. The first element is a List<String> of instructor ids,
	 * the second element is a List<String> of student ids.
	 */
	public Map<String, List<String>> getInstructorsStudentsForSite(String sakaiSiteID) {
		Map togo = null;

		List<String> instructorIds = new ArrayList<String>();
		List<String> studentIds = new ArrayList<String>();
		Document doc = getEnrollmentDocument(sakaiSiteID);
		NodeList instructors = doc.getElementsByTagName("instructor");

		for (int i = 0; i < instructors.getLength(); i++) {
			Element nextInst = (Element) instructors.item(i);
			String instUID = nextInst.getElementsByTagName("uid").item(0).getTextContent();
			instructorIds.add(instUID);
		}

		NodeList students = doc.getElementsByTagName("student");

		for (int i = 0 ; i < students.getLength(); i++) {
			Element nextStud = (Element) students.item(i);
			String studUID = nextStud.getElementsByTagName("uid").item(0).getTextContent();
			studentIds.add(studUID);
		}

		togo = new HashMap<String, List<String>>();
		togo.put("instructor", instructorIds);
		togo.put("student", studentIds);

		return togo;
	}

	/**
	 * This method swap a users role in a Turnitin site. The currentRole should
	 * be accurate for the users current information otherwise the method may
	 * fail (this all depends on calls to Turnitin's Webservice API's). So if
	 * you pass in a site, a user, and the value 1 (student) that user should be
	 * switched to an instructor in that site.
	 * 
	 * @param siteId
	 * @param user
	 * @param currentRole The current role using Turnitin codes. In Turnitin a 
	 * value of 1 always represents a student and a value of 2 represents an
	 * instructor.
	 * @return
	 */
	public boolean swapTurnitinRoles(String siteId, User user, int currentRole ) {
		boolean togo = false;
		
		if (user != null) {
			// TODO We need to centralize packing user options since sometimes the 
			// email address may come from their Profile Tool profile
			Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
					"fid","19","fcmd", "3", "uem", user.getEmail(), "uid", user.getId(),
					"ufn", user.getFirstName(), "uln", user.getLastName(), 
					"username", user.getDisplayName(), "ctl", siteId, "cid", siteId,
					"utp", currentRole+"", 
					"tem", turnitinConn.getInstructorInfo(siteId).get("uem"));

			Map ret = new HashMap();
			try {
				ret = turnitinConn.callTurnitinWDefaultsReturnMap(params);
			} catch (SubmissionException e) {
				log.error("Error syncing Turnitin site: " + siteId + " userid: " + user.getId(), e);
			} catch (TransientSubmissionException e) {
				log.error("Error syncing Turnitin site: " + siteId + " userid: " + user.getId(), e);
			}

			// A Successful return should look like:
			// {rmessage=Successful!, rcode=93}
			if (ret.containsKey("rcode") && ret.get("rcode").equals("93")) {
				log.info("Successfully swapped roles for site: " + siteId + " user: " + user.getEid() + " oldRole: " + currentRole);
				togo = true;
			}
		}
		else {
			// This was successful because the user doesn't exist in our Sakai
			// installation, and so we don't need to sync them at all.
			togo = true; 
		}

		return togo;
	}

	/**
	 * Looks up a Sakai {@link org.sakaiproject.user.api.User} by userid, 
	 * returns null if they do not exist.
	 * 
	 * @param userid
	 * @return the User object or null if the user does not exist in the Sakai
	 * installation.
	 */
	public User getUser(String userid) {
		User user = null;
		try {
			user = userDirectoryService.getUser(userid);
		} catch (UserNotDefinedException e) {
			log.warn("Attemping to lookup user for Turnitn Sync that does not exist: " + userid, e);
		}
		return user;
	}

	/**
	 * The primary method of this class. Syncs the enrollment between a Sakai
	 * Site and it's corresponding 
	 * 
	 * @param sakaiSiteID
	 */
	public boolean syncSiteWithTurnitin(String sakaiSiteID) {
		boolean success = true;

		Map<String, List<String>> enrollment = getInstructorsStudentsForSite(sakaiSiteID);

		Site site = null;
		try {
			site = siteService.getSite(sakaiSiteID);
		} catch (IdUnusedException e) {
			throw new IllegalArgumentException("The Sakai Site with ID: " + sakaiSiteID + " does not exist.");
		}


		for (String uid: enrollment.get("instructor")) {
			if (!site.isAllowed(uid, "section.role.instructor")) {
				boolean status = swapTurnitinRoles(sakaiSiteID, getUser(uid), 2);
				if (status == false) {
					success = false;
				}
			}
		}

		for (String uid: enrollment.get("student")) {
			if (site.isAllowed(uid, "section.role.instructor")) {
				boolean status = swapTurnitinRoles(sakaiSiteID, getUser(uid), 1);
				if (status == false) {
					success = false;
				}
			}
		}

		return success;
	}

	/**
	 * Utility method to create the Lock ID that will be used in the 
	 * Content Review Lock table to hold on to a Sakai Site while we try to 
	 * sync it.
	 * 
	 * @param item
	 * @return
	 */
	public String makeLockID(ContentReviewRosterSyncItem item) {
		return item.getClass().getCanonicalName() + item.getId();
	}

	private boolean obtainLock(ContentReviewRosterSyncItem item) {
		return dao.obtainLock(makeLockID(item), serverConfigurationService.getServerId(), LOCK_PERIOD);
	}

	private void releaseLock(ContentReviewRosterSyncItem item) {
		dao.releaseLock(makeLockID(item), serverConfigurationService.getServerId());
	}

	/**
	 * This is the main processing method that's meant to be periodically run
	 * by a quartz job or other script. It will sync all the Sakai Sites that
	 * have been put in the queue due to site updates or something.
	 */
	public void processSyncQueue() {
		Restriction notStarted = new Restriction("status", ContentReviewRosterSyncItem.NOT_STARTED_STATUS, Restriction.EQUALS);
		Restriction failed = new Restriction("status", ContentReviewRosterSyncItem.FAILED_STATUS, Restriction.EQUALS);
		Order order = new Order("status", true);
		Search search = new Search(new Restriction[] {notStarted,failed}, order);
		search.setConjunction(false); // OR matching
		List<ContentReviewRosterSyncItem> items = dao.findBySearch(ContentReviewRosterSyncItem.class, search);
		for (ContentReviewRosterSyncItem item: items) {
			if (obtainLock(item)) {
				log.info("About to Turnitin Syncing: " + item.getId() + " , " + item.getSiteId() + " , " + item.getStatus());
				item.setLastTried(new Date());
				try {
					boolean success = syncSiteWithTurnitin(item.getSiteId());
					if (success) {
						item.setStatus(ContentReviewRosterSyncItem.FINISHED_STATUS);
					}
					else {
						item.setStatus(ContentReviewRosterSyncItem.FAILED_STATUS);
					}
				} catch (Exception e) {
					item.setStatus(ContentReviewRosterSyncItem.FAILED_STATUS);
					log.error("Unable to complete Turnitin Roster Sync for SyncItem id: " 
						+ item.getId() + " + siteid: " + item.getSiteId(), e);
					StringBuilder sb = item.getMessages() == null ? new StringBuilder() : new StringBuilder(item.getMessages());
					sb.append("\n"+item.getLastTried().toLocaleString()
						+"Unable to complete Turnitin Roster Sync. See log for full stack trace\n");
					sb.append(e.getMessage());
					item.setMessages(sb.toString());
				} finally {
					dao.update(item);
					releaseLock(item);
				}
			}
		}
	}
}
