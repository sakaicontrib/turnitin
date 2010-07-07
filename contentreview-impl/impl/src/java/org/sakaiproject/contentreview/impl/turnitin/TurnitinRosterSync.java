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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransientSubmissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return togo;
	}

	/**
	 * This will make an API call to turnitit to fetch the list of instructors
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

	public boolean swapTurnitinRoles(String siteId, User user, int currentRole ) {
		boolean togo = false;
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransientSubmissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// A Successful return should look like:
		// {rmessage=Successful!, rcode=93}
		if (ret.containsKey("rcode") && ret.get("rcode").equals("93")) {
			log.info("Successfully swapped roles for site: " + siteId + " user: " + user.getEid() + " oldRole: " + currentRole);
			togo = true;
		}

		return togo;
	}

	public User getUser(String userid) {
		User user = null;
		try {
			user = userDirectoryService.getUser(userid);
		} catch (UserNotDefinedException e) {
			throw new IllegalArgumentException("User Does not Exist: " + userid, e);
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
			Member member = site.getMember(uid);
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

	public String makeLockID(ContentReviewRosterSyncItem item) {
		return item.getClass().getCanonicalName() + item.getId();
	}

	private boolean obtainLock(ContentReviewRosterSyncItem item) {
		return dao.obtainLock(makeLockID(item), serverConfigurationService.getServerId(), LOCK_PERIOD);
	}

	private void releaseLock(ContentReviewRosterSyncItem item) {
		dao.releaseLock(makeLockID(item), serverConfigurationService.getServerId());
	}

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
				boolean success = syncSiteWithTurnitin(item.getSiteId());
				if (success) {
					item.setStatus(ContentReviewRosterSyncItem.FINISHED_STATUS);
				}
				else {
					item.setStatus(ContentReviewRosterSyncItem.FAILED_STATUS);
				}
				dao.update(item);
				releaseLock(item);
			}
		}
	}
}
