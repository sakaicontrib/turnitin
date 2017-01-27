/**********************************************************************************
 * $URL$
 * $Id$
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.EmailValidator;
import org.hibernate.criterion.Restrictions;
import org.tsugi.basiclti.BasicLTIConstants;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
import org.sakaiproject.assignment.api.AssignmentContent;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.assignment.api.AssignmentSubmission;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;
import org.sakaiproject.contentreview.exception.QueueException;
import org.sakaiproject.contentreview.exception.ReportException;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.sakaiproject.contentreview.impl.hbm.BaseReviewServiceImpl;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.genericdao.api.search.Restriction;
import org.sakaiproject.genericdao.api.search.Search;
import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.service.gradebook.shared.AssessmentNotFoundException;
import org.sakaiproject.service.gradebook.shared.GradebookExternalAssessmentService;
import org.sakaiproject.service.gradebook.shared.GradebookService;
import org.sakaiproject.service.gradebook.shared.GradebookNotFoundException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.turnitin.util.TurnitinAPIUtil;
import org.sakaiproject.turnitin.util.TurnitinLTIUtil;
import org.sakaiproject.turnitin.util.TurnitinReturnValue;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.ResourceLoader;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.sakaiproject.contentreview.turnitin.TurnitinConstants;

public class TurnitinReviewServiceImpl extends BaseReviewServiceImpl {

	private static final Log log = LogFactory.getLog(TurnitinReviewServiceImpl.class);

	public static final String TURNITIN_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

	private static final String SERVICE_NAME="Turnitin";

	// Site property to enable or disable use of Turnitin for the site
	private static final String TURNITIN_SITE_PROPERTY = "turnitin";

	final static long LOCK_PERIOD = 12000000;

	private boolean studentAccountNotified = true;

	private int sendSubmissionNotification = 0;

	private Long maxRetry = null;
	public void setMaxRetry(Long maxRetry) {
		this.maxRetry = maxRetry;
	}

	//note that the assignment id actually has to be unique globally so use this as a prefix
	// eg. assignid = defaultAssignId + siteId
	private String defaultAssignId = null;

	private String defaultClassPassword = null;

	private List<String> enabledSiteTypes;

	// These are error messages from turnitin for which the item should never be retried, because it will never recover (Eg. less than 20 words, invalid file, etc.)
	private Set<String> terminalQueueErrors;

	// Define Turnitin's acceptable file extensions and MIME types, order of these arrays DOES matter
	private final String[] DEFAULT_ACCEPTABLE_FILE_EXTENSIONS = new String[] {
		".doc", 
		".docx", 
		".xls", 
		".xls", 
		".xls", 
		".xls", 
		".xlsx", 
		".ppt", 
		".ppt", 
		".ppt", 
		".ppt", 
		".pptx", 
		".pps", 
		".pps", 
		".ppsx", 
		".pdf", 
		".ps", 
		".eps", 
		".txt", 
		".html", 
		".htm", 
		".wpd", 
		".wpd", 
		".odt", 
		".rtf", 
		".rtf", 
		".rtf", 
		".rtf", 
		".hwp"
	};
	private final String[] DEFAULT_ACCEPTABLE_MIME_TYPES = new String[] {
		"application/msword", 
		"application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
		"application/excel", 
		"application/vnd.ms-excel", 
		"application/x-excel", 
		"application/x-msexcel", 
		"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
		"application/mspowerpoint", 
		"application/powerpoint", 
		"application/vnd.ms-powerpoint", 
		"application/x-mspowerpoint", 
		"application/vnd.openxmlformats-officedocument.presentationml.presentation", 
		"application/mspowerpoint", 
		"application/vnd.ms-powerpoint", 
		"application/vnd.openxmlformats-officedocument.presentationml.slideshow", 
		"application/pdf", 
		"application/postscript", 
		"application/postscript", 
		"text/plain", 
		"text/html", 
		"text/html", 
		"application/wordperfect", 
		"application/x-wpwin", 
		"application/vnd.oasis.opendocument.text", 
		"text/rtf", 
		"application/rtf", 
		"application/x-rtf", 
		"text/richtext", 
		"application/x-hwp"
	};

	// Sakai.properties overriding the arrays above
	private final String PROP_ACCEPT_ALL_FILES = "turnitin.accept.all.files";

	private final String PROP_ACCEPTABLE_FILE_EXTENSIONS = "turnitin.acceptable.file.extensions";
	private final String PROP_ACCEPTABLE_MIME_TYPES = "turnitin.acceptable.mime.types";

	// A list of the displayable file types (ie. "Microsoft Word", "WordPerfect document", "Postscript", etc.)
	private final String PROP_ACCEPTABLE_FILE_TYPES = "turnitin.acceptable.file.types";

	private final String KEY_FILE_TYPE_PREFIX = "file.type";

	private TurnitinAccountConnection turnitinConn;
	public void setTurnitinConn(TurnitinAccountConnection turnitinConn) {
		this.turnitinConn = turnitinConn;
	}

	/**
	 *  Setters
	 */

	private ServerConfigurationService serverConfigurationService;

	public void setServerConfigurationService (ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	private EntityManager entityManager;

	public void setEntityManager(EntityManager en){
		this.entityManager = en;
	}

	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}

	private SakaiPersonManager sakaiPersonManager;
	public void setSakaiPersonManager(SakaiPersonManager s) {
		this.sakaiPersonManager = s;
	}

	private ContentReviewDao dao;
	public void setDao(ContentReviewDao dao) {
		super.setDao(dao);
		this.dao = dao;
	}

	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		super.setUserDirectoryService(userDirectoryService);
		this.userDirectoryService = userDirectoryService;
	}

	private SiteService siteService;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	private SqlService sqlService;
	public void setSqlService(SqlService sql) {
		sqlService = sql;
	}


	private PreferencesService preferencesService;
	public void setPreferencesService(PreferencesService preferencesService) {
		this.preferencesService = preferencesService;
	}

	private TurnitinContentValidator turnitinContentValidator;
	public void setTurnitinContentValidator(TurnitinContentValidator turnitinContentValidator) {
		this.turnitinContentValidator = turnitinContentValidator;
	}
	
	private AssignmentService assignmentService;
	public void setAssignmentService(AssignmentService assignmentService) {
		this.assignmentService = assignmentService;
	}
	
	private TurnitinLTIUtil tiiUtil;
	public void setTiiUtil(TurnitinLTIUtil tiiUtil) {
		this.tiiUtil = tiiUtil;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}
	
	private SecurityService securityService;
	public void setSecurityService(SecurityService securityService) {
		this.securityService = securityService;
	}
	
	/**
	 *  If set to true in properties, will result in 3 random digits being appended
	 *  to the email name. In other words, adrian.r.fish@gmail.com will become something
	 *  like adrian.r.fish593@gmail.com
	 */
	private boolean spoilEmailAddresses = false;

	/** Prefer system profile email addresses */
	private boolean preferSystemProfileEmail = true;

	/** Use guest account eids as email addresses */
	private boolean preferGuestEidEmail = true;

	private GradebookService gradebookService = (GradebookService)
			ComponentManager.get("org.sakaiproject.service.gradebook.GradebookService");
	private GradebookExternalAssessmentService gradebookExternalAssessmentService =
			(GradebookExternalAssessmentService)ComponentManager.get("org.sakaiproject.service.gradebook.GradebookExternalAssessmentService");

	/**
	 * Place any code that should run when this class is initialized by spring
	 * here
	 */

	public void init() {

		studentAccountNotified = turnitinConn.isStudentAccountNotified();
		sendSubmissionNotification = turnitinConn.getSendSubmissionNotification();
		maxRetry = turnitinConn.getMaxRetry();
		defaultAssignId = turnitinConn.getDefaultAssignId();
		defaultClassPassword = turnitinConn.getDefaultClassPassword();

		spoilEmailAddresses = serverConfigurationService.getBoolean("turnitin.spoilEmailAddresses", false);
		preferSystemProfileEmail = serverConfigurationService.getBoolean("turnitin.preferSystemProfileEmail", true);
		preferGuestEidEmail = serverConfigurationService.getBoolean("turnitin.preferGuestEidEmail", true);

		enabledSiteTypes = Arrays.asList(ArrayUtils.nullToEmpty(serverConfigurationService.getStrings("turnitin.sitetypes")));

		String[] strTerminalQueueErrors = serverConfigurationService.getStrings("turnitin.terminalQueueErrors");
		if (strTerminalQueueErrors == null)
		{
			strTerminalQueueErrors = new String[]{"Your submission does not contain valid text.", "Your submission must contain 20 words or more.", "You must upload a supported file type for this assignment."};
		}
		terminalQueueErrors = new HashSet<>(strTerminalQueueErrors.length);
		Collections.addAll(terminalQueueErrors, strTerminalQueueErrors);

		log.info("init(): spoilEmailAddresses=" + spoilEmailAddresses + 
		          " preferSystemProfileEmail=" + preferSystemProfileEmail + 
		          " preferGuestEidEmail=" + preferGuestEidEmail);

		if (siteAdvisor != null) {
			log.info("Using siteAdvisor: " + siteAdvisor.getClass().getName());
		}

		if (enabledSiteTypes != null && !enabledSiteTypes.isEmpty()) {
			log.info("Turnitin is enabled for site types: " + StringUtils.join(enabledSiteTypes, ","));
		}

		if (!turnitinConn.isUseSourceParameter()) {
			if (serverConfigurationService.getBoolean("turnitin.updateAssingments", false))
				doAssignments();
		}

	}

	public String getServiceName() {
		return SERVICE_NAME;
	}

	/**
	 * Allow Turnitin for this site?
	 * @param s
	 * @return 
	 */
	public boolean isSiteAcceptable(Site s) {

		if (s == null) {
			return false;
		}

		log.debug("isSiteAcceptable: " + s.getId() + " / " + s.getTitle());

		// Delegated to another bean
		if (siteAdvisor != null) {
			return siteAdvisor.siteCanUseReviewService(s);
		}

		// Check site property
		ResourceProperties properties = s.getProperties();

                String prop = (String) properties.get(TURNITIN_SITE_PROPERTY);
                if (prop != null) {
			log.debug("Using site property: " + prop);
                        return Boolean.parseBoolean(prop);
                }

		// Check list of allowed site types, if defined
		if (enabledSiteTypes != null && !enabledSiteTypes.isEmpty()) {
			log.debug("Using site type: " + s.getType());
			return enabledSiteTypes.contains(s.getType());
		}

		// No property set, no restriction on site types, so allow
        return true; 
    }
	
	public boolean isDirectAccess(Site s) {
		if (s == null) {
			return false;
		}

		log.debug("isDirectAccess: " + s.getId() + " / " + s.getTitle());
		// Delegated to another bean
		
		return siteAdvisor != null && siteAdvisor.siteCanUseReviewService(s) && siteAdvisor.siteCanUseLTIReviewService(s) && siteAdvisor.siteCanUseLTIDirectSubmission(s);
	}
	
	@Override
	public boolean isDirectAccess(Site s, Date assignmentCreationDate)
	{
		if (s == null || siteAdvisor == null)
		{
			return false;
		}
		
		return siteAdvisor.siteCanUseReviewService(s) && siteAdvisor.siteCanUseLTIReviewServiceForAssignment(s, assignmentCreationDate)
				&& siteAdvisor.siteCanUseLTIDirectSubmission(s);
	}

	public boolean allowMultipleAttachments()
	{
		return serverConfigurationService.getBoolean("turnitin.allow.multiple.attachments", false);
	}


	public String getIconUrlforScore(Long score) {

		String urlBase = "/sakai-contentreview-tool-tii/images/score_";
		String suffix = ".gif";

		if (score.equals(Long.valueOf(0))) {
			return urlBase + "blue" + suffix;
		} else if (score.compareTo(Long.valueOf(25)) < 0 ) {
			return urlBase + "green" + suffix;
		} else if (score.compareTo(Long.valueOf(50)) < 0  ) {
			return urlBase + "yellow" + suffix;
		} else if (score.compareTo(Long.valueOf(75)) < 0 ) {
			return urlBase + "orange" + suffix;
		} else {
			return urlBase + "red" + suffix;
		}

	}
	
	public String getIconColorforScore(Long score) {

		if (score.equals(Long.valueOf(0))) {
			return "blue";
		} else if (score.compareTo(Long.valueOf(25)) < 0 ) {
			return "green";
		} else if (score.compareTo(Long.valueOf(50)) < 0  ) {
			return "yellow";
		} else if (score.compareTo(Long.valueOf(75)) < 0 ) {
			return "orange";
		} else {
			return "red";
		}

	}




	/**
	 * This uses the default Instructor information or current user.
	 *
	 * @param contentId
	 * @param assignmentRef
	 * @param userId
	 * @return 
	 * @throws org.sakaiproject.contentreview.exception.QueueException
	 * @throws org.sakaiproject.contentreview.exception.ReportException
	 * @see org.sakaiproject.contentreview.impl.hbm.BaseReviewServiceImpl#getReviewReportInstructor(java.lang.String)
	 */
	public String getReviewReportInstructor(String contentId, String assignmentRef, String userId) throws QueueException, ReportException {

		Search search = new Search();
		search.addRestriction(new Restriction("contentId", contentId));
		List<ContentReviewItem> matchingItems = dao.findBySearch(ContentReviewItem.class, search);
		if (matchingItems.isEmpty()) {
			log.debug("Content " + contentId + " has not been queued previously");
			throw new QueueException("Content " + contentId + " has not been queued previously");
		}

		if (matchingItems.size() > 1)
			log.debug("More than one matching item found - using first item found");

		// check that the report is available
		// TODO if the database record does not show report available check with
		// turnitin (maybe)

		ContentReviewItem item = (ContentReviewItem) matchingItems.iterator().next();
		if (item.getStatus().compareTo(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE) != 0) {
			log.debug("Report not available: " + item.getStatus());
			throw new ReportException("Report not available: " + item.getStatus());
		}

		// report is available - generate the URL to display

		String oid = item.getExternalId();
		String fid = "6";
		String fcmd = "1";
		String cid = item.getSiteId();
		String assignid = defaultAssignId + item.getSiteId();
		String utp = "2";

		Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
				"fid", fid,
				"fcmd", fcmd,
				"assignid", assignid,
				"cid", cid,
				"oid", oid,
				"utp", utp
		);

		params.putAll(getInstructorInfo(item.getSiteId()));

		return turnitinConn.buildTurnitinURL(params);
	}

	public String getReviewReportStudent(String contentId, String assignmentRef, String userId) throws QueueException, ReportException {

		Search search = new Search();
		search.addRestriction(new Restriction("contentId", contentId));
		List<ContentReviewItem> matchingItems = dao.findBySearch(ContentReviewItem.class, search);
		if (matchingItems.isEmpty()) {
			log.debug("Content " + contentId + " has not been queued previously");
			throw new QueueException("Content " + contentId + " has not been queued previously");
		}

		if (matchingItems.size() > 1)
			log.debug("More than one matching item found - using first item found");

		// check that the report is available
		// TODO if the database record does not show report available check with
		// turnitin (maybe)

		ContentReviewItem item = (ContentReviewItem) matchingItems.iterator().next();
		if (item.getStatus().compareTo(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE) != 0) {
			log.debug("Report not available: " + item.getStatus());
			throw new ReportException("Report not available: " + item.getStatus());
		}


		// report is available - generate the URL to display

		String oid = item.getExternalId();
		String fid = "6";
		String fcmd = "1";
		String cid = item.getSiteId();
		String assignid = defaultAssignId + item.getSiteId();

		User user = userDirectoryService.getCurrentUser();

		//USe the method to get the correct email
		String uem = getEmail(user);
		String ufn = getUserFirstName(user);
		String uln = getUserLastName(user);
		String uid = item.getUserId();
		String utp = "1";

		Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
				"fid", fid,
				"fcmd", fcmd,
				"assignid", assignid,
				"uid", uid,
				"cid", cid,
				"oid", oid,
				"uem", uem,
				"ufn", ufn,
				"uln", uln,
				"utp", utp
		);

		return turnitinConn.buildTurnitinURL(params);
	}

	public String getReviewReport(String contentId, String assignmentRef, String userId)
	throws QueueException, ReportException {

		log.debug("getReviewReport for LTI integration");
		//should have already checked lti integration on assignments tool
		Search search = new Search();
		search.addRestriction(new Restriction("contentId", contentId));
		List<ContentReviewItem> matchingItems = dao.findBySearch(ContentReviewItem.class, search);
		if (matchingItems.isEmpty()) {
			log.debug("Content " + contentId + " has not been queued previously");
			throw new QueueException("Content " + contentId + " has not been queued previously");
		}

		if (matchingItems.size() > 1)
			log.debug("More than one matching item found - using first item found");

		// check that the report is available
		ContentReviewItem item = (ContentReviewItem) matchingItems.iterator().next();
		if (item.getStatus().compareTo(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE) != 0) {
			log.debug("Report not available: " + item.getStatus());
			throw new ReportException("Report not available: " + item.getStatus());
		}
		
		return getLTIReportAccess(item);
	}

    /**
    * Get additional data from String if available
    * @return array containing site ID, Task ID, Task Title
    */
    private String[] getAssignData(String data){
        String[] assignData = null;
        try{
            if(data.contains("#")){
                assignData = data.split("#");
            }
        }catch(Exception e){
        }
        return assignData;
    }

    public String getInlineTextId(String assignmentReference, String userId, long submissionTime){
	return "";
    } 	

    public boolean acceptInlineAndMultipleAttachments(){
	return false;
    }

    public int getReviewScore(String contentId, String assignmentRef, String userId) throws QueueException,
                        ReportException, Exception {
            ContentReviewItem item=null;
            try{
                        List<ContentReviewItem> matchingItems = getItemsByContentId(contentId);
                        if (matchingItems.isEmpty()) {
                                log.debug("Content " + contentId + " has not been queued previously");
                        }
                        if (matchingItems.size() > 1)
                                log.debug("More than one matching item - using first item found");

                        item = (ContentReviewItem) matchingItems.iterator().next();
                        if (item.getStatus().compareTo(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE) != 0) {
                                log.debug("Report not available: " + item.getStatus());
                        }
            }catch(Exception e){
                log.error("(getReviewScore)"+e);
            }

            Site s = null;
            try {
                s = siteService.getSite(item.getSiteId());
            } catch (IdUnusedException iue) {
                log.warn("getReviewScore: Site " + item.getSiteId() + " not found!" + iue.getMessage());
            }

            //////////////////////////////  NEW LTI INTEGRATION  ///////////////////////////////
            try
            {
                org.sakaiproject.assignment.api.Assignment asn = assignmentService.getAssignment(assignmentRef);
                if(s != null && asn != null && siteAdvisor.siteCanUseLTIReviewServiceForAssignment(s, new Date(asn.getTimeCreated().getTime()))){
                    log.debug("getReviewScore using the LTI integration");			
                    return item.getReviewScore();
                }
            }
            catch (IdUnusedException | PermissionException e)
            {
                log.warn("getReviewScore: Assignment " + assignmentRef + " not found!" + e.getMessage(), e);
            }
            //////////////////////////////  OLD API INTEGRATION  ///////////////////////////////
            String[] assignData = null;
            try{
                   assignData = getAssignData(contentId);
            }catch(Exception e){
                log.error("(assignData)"+e);
            }

            String siteId,taskId,taskTitle;
            Map<String,Object> data = new HashMap<>();
            if(assignData != null){
                siteId = assignData[0];
                taskId = assignData[1];
                taskTitle = assignData[2];
            }else{
                siteId = item.getSiteId();
                taskId = item.getTaskId();
                taskTitle = getAssignmentTitle(taskId);
                data.put("assignment1","assignment1");
            }
            //Sync Grades
            if(turnitinConn.getUseGradeMark()){
                try{
                    data.put("siteId", siteId);
                    data.put("taskId", taskId);
                    data.put("taskTitle", taskTitle);
                    syncGrades(data);
                }catch(Exception e){
                    log.error("Error syncing grades. "+e);
                }
            }

            return item.getReviewScore();
        }

        /**
         * Check if grade sync has been run already for the specified site
         * @param sess Current Session
         * @param taskId
         * @return
         */
        public boolean gradesChecked(Session sess, String taskId){
            String sessSync;
            try{
                sessSync = sess.getAttribute("sync").toString();
                if(sessSync.equals(taskId)){
                    return true;
                }
            }catch(Exception e){
                //log.error("(gradesChecked)"+e);
            }
            return false;
        }

        /**
    * Check if the specified user has the student role on the specified site.
    * @param siteId Site ID
    * @param userId User ID
    * @return true if user has student role on the site.
    */
        public boolean isUserStudent(String  siteId, String userId){
            boolean isStudent=false;
            try{
                        Set<String> studentIds = siteService.getSite(siteId).getUsersIsAllowed("section.role.student");
                        List<User> activeUsers = userDirectoryService.getUsers(studentIds);
                        for (int i = 0; i < activeUsers.size(); i++) {
                            User user = activeUsers.get(i);
                            if(userId.equals(user.getId())){
                                return true;
                            }
                        }
                }catch(Exception e){
                    log.info("(isStudentUser)"+e);
                }
            return isStudent;
        }

        /**
    * Return the Gradebook item associated with an assignment.
    * @param data Map containing Site/Assignment IDs
    * @return Associated gradebook item
    */
        public Assignment getAssociatedGbItem(Map data){
            Assignment assignment = null;
            String taskId = data.get("taskId").toString();
            String siteId = data.get("siteId").toString();
            String taskTitle = data.get("taskTitle").toString();

            pushAdvisor();
            try {
                List<Assignment> allGbItems = gradebookService.getAssignments(siteId);
                for (Assignment assign : allGbItems) {
                        //Match based on External ID / Assignment title
                        if(taskId.equals(assign.getExternalId()) || assign.getName().equals(taskTitle) ){
                            assignment = assign;
                            break;
                        }
                }
            } catch (Exception e) {
                    log.error("(allGbItems)"+e.toString());
            } finally{
                popAdvisor();
            }
            return assignment;
        }

        /**
    * Check Turnitin for grades and write them to the associated gradebook
    * @param data Map containing relevant IDs (site ID, Assignment ID, Title)
    */
        public void syncGrades(Map<String,Object>data){
                //Get session and check if gardes have already been synced
                Session sess = sessionManager.getCurrentSession();
                boolean runOnce=gradesChecked(sess, data.get("taskId").toString());
                boolean isStudent = isUserStudent(data.get("siteId").toString(), sess.getUserId());

                if(turnitinConn.getUseGradeMark() && runOnce == false && isStudent == false){
                    log.info("Syncing Grades with Turnitin");

                    String siteId = data.get("siteId").toString();
                    String taskId = data.get("taskId").toString();

                    HashMap<String, Integer> reportTable = new HashMap<>();
                    HashMap<String, String> additionalData = new HashMap<>();
                    String tiiUserId;

                    String assign = taskId;
                    if(data.containsKey("assignment1")){
                        //Assignments 1 uses the actual title whereas Assignments 2 uses the ID
                        assign = getAssignmentTitle(taskId);
                    }

                    //Run once
                    sess.setAttribute("sync", taskId);

                    //Get students enrolled on class in Turnitin
                    Map<String,Object> enrollmentInfo = getAllEnrollmentInfo(siteId);

                    //Get Associated GB item
                    Assignment assignment = getAssociatedGbItem(data);

                    //List submissions call
                    Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
                                "fid", "10",
                                "fcmd", "2",
                                "tem", getTEM(siteId),
                                "assign", assign,
                                "assignid", taskId,
                                "cid", siteId,
                                "ctl", siteId,
                                "utp", "2"
                    );
                    params.putAll(getInstructorInfo(siteId));

                    Document document = null;
                    try {
                            document = turnitinConn.callTurnitinReturnDocument(params);
                    }catch (TransientSubmissionException e) {
                        log.error(e);
                    }catch (SubmissionException e) {
                        log.warn("SubmissionException error. "+e);
                    }
                    Element root = document.getDocumentElement();
                    if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("72") == 0) {
                            NodeList objects = root.getElementsByTagName("object");
                            String grade;
                            log.debug(objects.getLength() + " objects in the returned list");

                            for (int i=0; i<objects.getLength(); i++) {
                                    tiiUserId = ((CharacterData) (((Element)(objects.item(i))).getElementsByTagName("userid").item(0).getFirstChild())).getData().trim();
                                    additionalData.put("tiiUserId",tiiUserId);
                                    //Get GradeMark Grade
                                    try{
                                        grade = ((CharacterData) (((Element)(objects.item(i))).getElementsByTagName("score").item(0).getFirstChild())).getData().trim();
                                        reportTable.put("grade"+tiiUserId, Integer.valueOf(grade));
                                    } catch(DOMException | NumberFormatException e){
                                        //No score returned
                                        grade="";
                                    } catch(Exception e) {
                                        grade="";
                                        log.error( "Unexpected exception getting grade", e );
                                    }

                                    if(!grade.equals("")){
                                        //Update Grade ----------------
                                        if(gradebookService.isGradebookDefined(siteId)){
                                            writeGrade(assignment,data,reportTable,additionalData,enrollmentInfo);
                                        }
                                    }
                            }
                    } else {
                            log.debug("Report list request not successful");
                            log.debug(document.getTextContent());
                    }
                }
            }

    /**
    * Check if a grade returned from Turnitin is greater than the max points for
    * an assignment. If so then set to max points.
    * (Grade is unchanged in Turnitin)
    * @param grade Grade returned from Turnitin
    * @param assignment
    * @return
    */
    public String processGrade(String grade,Assignment assignment){
        String processedGrade="";
        try{
            int gradeVal = Integer.parseInt(grade);
            if(gradeVal > assignment.getPoints()){
                processedGrade = Double.toString(assignment.getPoints());
                log.info("Grade exceeds maximum point value for this assignment("
                        +assignment.getName()+") Setting to Max Points value");
            }else{
                processedGrade = grade;
            }
        }catch(NumberFormatException e){
            log.warn("Error parsing grade");
        }catch(Exception e){
            log.warn("Error processing grade");
        }
        return processedGrade;
    }


    /**
     * Write a grade to the gradebook for the current specified user
     * @param assignment
     * @param data
     * @param reportTable
     * @param additionalData
     * @param enrollmentInfo
     * @return
     */
    public boolean writeGrade(Assignment assignment, Map<String,Object> data, HashMap reportTable,HashMap additionalData,Map enrollmentInfo){
            boolean success = false;
            String grade;
            String siteId = data.get("siteId").toString();
            String currentStudentUserId = additionalData.get("tiiUserId").toString();
            String tiiExternalId ="";

            if(!enrollmentInfo.isEmpty()){
                if(enrollmentInfo.containsKey(currentStudentUserId)){
                    tiiExternalId = enrollmentInfo.get(currentStudentUserId).toString();
                    log.info("tiiExternalId: "+tiiExternalId);
                }
            }else{
                return false;
            }

            //Check if the returned grade is greater than the maximum possible grade
            //If so then set to the maximum grade
            grade = processGrade(reportTable.get("grade"+currentStudentUserId).toString(),assignment);

            pushAdvisor();
            try {
                        if(grade!=null){
                                try{
                                    if(data.containsKey("assignment1")){
                                        gradebookExternalAssessmentService.updateExternalAssessmentScore(siteId, assignment.getExternalId(),tiiExternalId,grade);
                                    }else{
                                        gradebookService.setAssignmentScoreString(siteId, data.get("taskTitle").toString(), tiiExternalId, grade, "SYNC");
                                    }
                                    log.info("UPDATED GRADE ("+grade+") FOR USER ("+tiiExternalId+") IN ASSIGNMENT ("+assignment.getName()+")");
                                    success = true;
                                }catch(GradebookNotFoundException e){
                                    log.error("Error update grade GradebookNotFoundException "+e.toString());
                                }catch(AssessmentNotFoundException e){
                                    log.error("Error update grade "+e.toString());
                                }catch(Exception e){
                                    log.error("Unexpected exception updating grade", e);
                                }
                        }
            } catch (Exception e) {
                log.error("Error setting grade "+e.toString());
            } finally {
                    popAdvisor();
            }
            return success;
        }

        /**
   * Get a list of students enrolled on a class in Turnitin
   * @param siteId Site ID
   * @return Map containing Students turnitin / Sakai ID
   */
        public Map getAllEnrollmentInfo(String siteId){
                Map<String,String> enrollmentInfo=new HashMap();
                String tiiExternalId;//the ID sakai stores
                String tiiInternalId;//Turnitin internal ID
                User user = null;
                Map instructorInfo = getInstructorInfo(siteId,true);
                try{
                    user = userDirectoryService.getUser(instructorInfo.get("uid").toString());
                }catch(UserNotDefinedException e){
                    log.error("(getAllEnrollmentInfo)User not defined. "+e);
                }
                Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
                                "fid", "19",
                                "fcmd", "5",
                                "tem", getTEM(siteId),
                                "ctl", siteId,
                                "cid", siteId,
                                "utp", "2",
                                "uid", user.getId(),
                                "uem",getEmail(user),
                                "ufn",user.getFirstName(),
                                "uln",user.getLastName()
                );
                Document document = null;
                try {
                        document = turnitinConn.callTurnitinReturnDocument(params);
                }catch (TransientSubmissionException | SubmissionException e) {
                        log.warn("Failed to get enrollment data using user: "+user.getDisplayName(), e);
                }catch (Exception e) {
                        log.error( "Unexpected exception getting document", e );
                }

                Element root = document.getDocumentElement();
                if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("93") == 0) {
                        NodeList objects = root.getElementsByTagName("student");
                        for (int i=0; i<objects.getLength(); i++) {
                                tiiExternalId = ((CharacterData) (((Element)(objects.item(i))).getElementsByTagName("uid").item(0).getFirstChild())).getData().trim();
                                tiiInternalId = ((CharacterData) (((Element)(objects.item(i))).getElementsByTagName("userid").item(0).getFirstChild())).getData().trim();
                                enrollmentInfo.put(tiiInternalId, tiiExternalId);
                        }
                }
                return enrollmentInfo;
        }

         public void pushAdvisor() {
                securityService.pushAdvisor(new SecurityAdvisor() {
                        
                        public SecurityAdvisor.SecurityAdvice isAllowed(String userId, String function,
                                String reference) {
                                return SecurityAdvisor.SecurityAdvice.ALLOWED;
                        }
                });
        }
        public void popAdvisor() {
                securityService.popAdvisor();
        }

	/**
	 * This method was originally private, but is being made public for the
	 * moment so we can run integration tests. TODO Revisit this decision.
	 *
	 * @param siteId
	 * @throws SubmissionException
	 * @throws TransientSubmissionException
	 */
	@SuppressWarnings("unchecked")
	public void createClass(String siteId) throws SubmissionException, TransientSubmissionException {
		log.debug("Creating class for site: " + siteId);

		String cpw = defaultClassPassword;
		String ctl = siteId;
		String fcmd = "2";
		String fid = "2";
		String utp = "2"; 					//user type 2 = instructor
		String cid = siteId;

		Document document;

		Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
				"cid", cid,
				"cpw", cpw,
				"ctl", ctl,
				"fcmd", fcmd,
				"fid", fid,
				"utp", utp
		);

		params.putAll(getInstructorInfo(siteId));

		document = turnitinConn.callTurnitinReturnDocument(params);

		Element root = document.getDocumentElement();
		String rcode = ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim();

		if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("20") == 0 ||
				((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("21") == 0 ) {
			log.debug("Create Class successful");
		} else {
			if ("218".equals(rcode) || "9999".equals(rcode)) {
				throw new TransientSubmissionException("Create Class not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
			} else {
				throw new SubmissionException("Create Class not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
			}
		}
	}

	/**
	 * This returns the String that will be used as the Assignment Title
	 * in Turn It In.
	 *
	 * The current implementation here has a few interesting caveats so that
	 * it will work with both, the existing Assignments 1 integration, and
	 * the new Assignments 2 integration under development.
	 *
	 * We will check and see if the taskId starts with /assignment/. If it
	 * does we will look up the Assignment Entity on the legacy Entity bus.
	 * (not the entitybroker).  This needs some general work to be made
	 * generally modular ( and useful for more than just Assignments 1 and 2
	 * ). We will need to look at some more concrete use cases and then
	 * factor it accordingly in the future when the next scenerio is
	 * required.
	 *
	 * Another oddity is that to get rid of our hard dependency on Assignments 1
	 * we are invoking the getTitle method by hand. We probably need a
	 * mechanism to register a title handler or something as part of the
	 * setup process for new services that want to be reviewable.
	 *
	 * @param taskId
	 * @return
	 */
	private String getAssignmentTitle(String taskId){
		String togo = taskId;
		if (taskId.startsWith("/assignment/")) {
			try {
				Reference ref = entityManager.newReference(taskId);
				log.debug("got ref " + ref + " of type: " + ref.getType());
				EntityProducer ep = ref.getEntityProducer();

				Entity ent = ep.getEntity(ref);
				log.debug("got entity " + ent);
				String title = scrubSpecialCharacters(ent.getClass().getMethod("getTitle").invoke(ent).toString());
				log.debug("Got reflected assignemment title from entity " + title);
				togo = URLDecoder.decode(title,"UTF-8");
				
				togo=togo.replaceAll("\\W+","");

			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | UnsupportedEncodingException e) {
				log.debug( e );
			} catch (Exception e) {
				log.error( "Unexpected exception getting assignment title", e );
			}
		}

		// Turnitin requires Assignment titles to be at least two characters long
		if (togo.length() == 1) {
			togo = togo + "_";
		}

		return togo;

	}

    private String scrubSpecialCharacters(String title) {

        try {
            if (title.contains("&")) {
                title = title.replace('&', 'n');
            }
            if (title.contains("%")) {
                title = title.replace("%", "percent");
            }
        }
        catch (Exception e) {
            log.debug( e );
        }

        return title;
    }

	/**
	 * @param siteId
	 * @param taskId
	 * @throws SubmissionException
	 * @throws TransientSubmissionException
	 */
	public void createAssignment(String siteId, String taskId) throws SubmissionException, TransientSubmissionException {
		createAssignment(siteId, taskId, null);
	}

	/**
	 * Works by fetching the Instructor User info based on defaults or current
	 * user.
	 *
	 * @param siteId
	 * @param taskId
	 * @return
	 * @throws SubmissionException
	 * @throws TransientSubmissionException
	 */
	@SuppressWarnings("unchecked")
	public Map getAssignment(String siteId, String taskId) throws SubmissionException, TransientSubmissionException {
		String taskTitle = getAssignmentTitle(taskId);

		Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
			"assign", taskTitle, "assignid", taskId, "cid", siteId, "ctl", siteId,
			"fcmd", "7", "fid", "4", "utp", "2" );

		params.putAll(getInstructorInfo(siteId));

		return turnitinConn.callTurnitinReturnMap(params);
	}

	public void addTurnitinInstructor(Map userparams) throws SubmissionException, TransientSubmissionException {
		Map params = new HashMap();
		params.putAll(userparams);
		params.putAll(turnitinConn.getBaseTIIOptions());
		params.put("fid", "1");
		params.put("fcmd", "2");
		params.put("utp", "2");
		turnitinConn.callTurnitinReturnMap(params);
	}

	/**
	 * Creates or Updates an Assignment
	 *
	 * This method will look at the current user or default instructor for it's
	 * user information.
	 *
	 *
	 * @param siteId
	 * @param taskId an assignment reference
	 * @param extraAsnOpts
	 * @throws SubmissionException
	 * @throws TransientSubmissionException
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void createAssignment(String siteId, String asnId, Map<String, Object> extraAsnOpts) throws SubmissionException, TransientSubmissionException {
		syncAssignment(siteId, asnId, extraAsnOpts, null);
	}

	/**
	 * @inheritDoc
	 */
	public void offerIndividualExtension(String siteId, String asnId, Map<String, Object> extraAsnOpts, Date extensionDate) throws SubmissionException, TransientSubmissionException
	{
		syncAssignment(siteId, asnId, extraAsnOpts, extensionDate);
	}

	/**
	 * Syncs an assignment and handles individual student extensions
	 */
	public void syncAssignment(String siteId, String taskId, Map<String, Object> extraAsnnOpts, Date extensionDate) throws SubmissionException, TransientSubmissionException
	{
		Site s = null;
		try {
			s = siteService.getSite(siteId);
		}
		catch (IdUnusedException iue) {
			log.warn("createAssignment: Site " + siteId + " not found!" + iue.getMessage());
			throw new TransientSubmissionException("Create Assignment not successful. Site " + siteId + " not found");
		}
		org.sakaiproject.assignment.api.Assignment asn;
		try
		{
			asn = assignmentService.getAssignment(taskId);
		}
		catch (IdUnusedException|PermissionException e)
		{
			asn = null;
		}

		//////////////////////////////  NEW LTI INTEGRATION  ///////////////////////////////
		
		Optional<Date> asnCreationDateOpt = getAssignmentCreationDate(asn);
		if(asnCreationDateOpt.isPresent() && siteAdvisor.siteCanUseLTIReviewServiceForAssignment(s, asnCreationDateOpt.get())){
			log.debug("Creating new TII assignment using the LTI integration");
			
			String asnId = asnRefToId(taskId);  // taskId is an assignment reference, but we sometimes only want the assignment id
			String ltiId = getActivityConfigValue(TurnitinConstants.STEALTHED_LTI_ID, asnId, TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID, TurnitinConstants.PROVIDER_ID);
			String ltiReportsId = null;

			ltiReportsId = s.getProperties().getProperty("turnitin_reports_lti_id");
			log.debug("This assignment has associated the following LTI Reports id: " + ltiReportsId);
			
			Map<String,String> ltiProps = new HashMap<>();
			if (extraAsnnOpts == null){
				throw new TransientSubmissionException("Create Assignment not successful. Empty extraAsnnOpts map");
			}

			ltiProps.put("context_id", siteId);
			ltiProps.put("context_title", s.getTitle());
			String contextLabel = s.getTitle();
			if(s.getShortDescription() != null){
				contextLabel = s.getShortDescription();
			}
			ltiProps.put("context_label", contextLabel);
			ltiProps.put("resource_link_id", taskId);
			String title = extraAsnnOpts.get("title").toString();
			ltiProps.put("resource_link_title", title);
			String description = extraAsnnOpts.get("instructions").toString();
			if(description != null){
				description = description.replaceAll("\\<.*?>","");//TODO improve this
				int instructionsMax = serverConfigurationService.getInt("contentreview.instructions.max", 1000);
				if(description.length() > instructionsMax){
					description = description.substring(0, instructionsMax);
				}
			}
			ltiProps.put("resource_link_description", description);

			// TII-245
			if (!StringUtils.isBlank(ltiId))
			{
				// This is an existing LTI instance, need to handle student extensions
				handleIndividualExtension(extensionDate, taskId, extraAsnnOpts);
			}
			
			String custom = BasicLTIConstants.RESOURCE_LINK_ID + "=" + taskId;
			custom += "\n" + BasicLTIConstants.RESOURCE_LINK_TITLE + "=" + title;
			custom += "\n" + BasicLTIConstants.RESOURCE_LINK_DESCRIPTION + "=" + description;

			try
			{
				long timestampOpen = (Long) extraAsnnOpts.get("timestampOpen");
				long timestampDue = (Long) extraAsnnOpts.get("timestampDue");
				// TII-245 - add a buffer to the TII due date to give time for the process queue job
				timestampDue += serverConfigurationService.getInt("contentreview.due.date.queue.job.buffer.minutes", 0) * 60000;
				ZonedDateTime open = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampOpen), ZoneOffset.UTC);
				ZonedDateTime due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampDue), ZoneOffset.UTC);
				// Turnitin requires dates in ISO8601 format. The example from their documentation is "2014-12-10T07:43:43Z".
				// This matches the Java formatter ISO_INSTANT
				String isoStart = open.format(DateTimeFormatter.ISO_INSTANT);
				String isoDue = due.format(DateTimeFormatter.ISO_INSTANT);
				ltiProps.put("startdate", isoStart);
				ltiProps.put("duedate", isoDue);
				ltiProps.put("feedbackreleasedate", isoDue);
				custom += "\n" + "startdate=" + isoStart;
				custom += "\n" + "duedate=" + isoDue;
				custom += "\n" + "feedbackreleasedate=" + isoDue;
			}
			catch (DateTimeException e)
			{
				log.error(e);
				throw new TransientSubmissionException("Create Assignment not successful. Invalid open and/or due date.");
			}
			
			ltiProps = putInstructorInfo(ltiProps, siteId);

			/*
			 * Force TII max points to 100 so we can interpret the result as a direct percentage.
			 * This is done because Assignments now has the ability to grade to an arbitrary number of decimal places.
			 * Due to the limitation of TII requiring whole integers for grading, we would have to inflate the grade by a
			 * factor to the power of the number of decimal places allowed. This would result in unusually large numbers
			 * on the TII, which could be confusing for the end user.
			 */
			ltiProps.put("maxpoints", "100");
			custom += "\n" + "maxpoints=100";

	        ltiProps.put("studentpapercheck", extraAsnnOpts.get("s_paper_check").toString());
	        ltiProps.put("journalcheck",extraAsnnOpts.get("journal_check").toString());

	        ltiProps.put("internetcheck", extraAsnnOpts.get("internet_check").toString());
	        ltiProps.put("institutioncheck",extraAsnnOpts.get("institution_check").toString());
			ltiProps.put("allow_non_or_submissions", extraAsnnOpts.get("allow_any_file").toString());

			//ONLY FOR TII UK
			//ltiProps.setProperty("anonymous_marking_enabled", extraAsnnOpts.get("s_paper_check"));
			
			custom += "\n" + "studentpapercheck=" + extraAsnnOpts.get("s_paper_check").toString();
			custom += "\n" + "journalcheck=" + extraAsnnOpts.get("journal_check").toString();
			custom += "\n" + "internetcheck=" + extraAsnnOpts.get("internet_check").toString();
			custom += "\n" + "institutioncheck=" + extraAsnnOpts.get("institution_check").toString();
			custom += "\n" + "allow_non_or_submissions=" + extraAsnnOpts.get("allow_any_file").toString();
 
			if (extraAsnnOpts.containsKey("exclude_type") && extraAsnnOpts.containsKey("exclude_value")){
				//exclude type 0=none, 1=words, 2=percentages
				String typeAux = "words";
				if(extraAsnnOpts.get("exclude_type").toString().equals("2")){
					typeAux = "percentage";
				}
				ltiProps.put("exclude_type", typeAux);
				ltiProps.put("exclude_value", extraAsnnOpts.get("exclude_value").toString());
				custom += "\n" + "exclude_type=" + typeAux;
				custom += "\n" + "exclude_value=" + extraAsnnOpts.get("exclude_value").toString();
			}

	        ltiProps.put("late_accept_flag", extraAsnnOpts.get("late_accept_flag").toString());
	        ltiProps.put("report_gen_speed", extraAsnnOpts.get("report_gen_speed").toString());
	        ltiProps.put("s_view_reports", extraAsnnOpts.get("s_view_report").toString());			
	        ltiProps.put("submit_papers_to", extraAsnnOpts.get("submit_papers_to").toString());
			
			custom += "\n" + "late_accept_flag=" + extraAsnnOpts.get("late_accept_flag").toString();			
			custom += "\n" + "report_gen_speed=" + extraAsnnOpts.get("report_gen_speed").toString();
			custom += "\n" + "s_view_reports=" + extraAsnnOpts.get("s_view_report").toString();
			custom += "\n" + "submit_papers_to=" + extraAsnnOpts.get("submit_papers_to").toString();

			if (extraAsnnOpts.containsKey("exclude_biblio")){
				ltiProps.put("use_biblio_exclusion", extraAsnnOpts.get("exclude_biblio").toString());
				custom += "\n" + "use_biblio_exclusion=" + extraAsnnOpts.get("exclude_biblio").toString();
			}
			if (extraAsnnOpts.containsKey("exclude_quoted")){
				ltiProps.put("use_quoted_exclusion", extraAsnnOpts.get("exclude_quoted").toString());
				custom += "\n" + "use_quoted_exclusion=" + extraAsnnOpts.get("exclude_quoted").toString();
			}
 			
			//adding callback url
			String callbackUrl = serverConfigurationService.getServerUrl() + "/sakai-contentreview-tool-tii/tii-servlet";
			log.debug("callbackUrl: " + callbackUrl);
			ltiProps.put("ext_resource_tool_placement_url", callbackUrl);
			
			TurnitinReturnValue result = tiiUtil.makeLTIcall(TurnitinLTIUtil.BASIC_ASSIGNMENT, null, ltiProps);
			if(result.getResult() < 0){
				log.error("Error making LTI call");
				throw new TransientSubmissionException("Create Assignment not successful. Check the logs to see message.");
			}
			
			Properties sakaiProps = new Properties();
			String globalId = tiiUtil.getGlobalTurnitinLTIToolId();
			String globalReportsId = tiiUtil.getGlobalTurnitinReportsLTIToolId();
			if(globalId == null){
				throw new TransientSubmissionException("Create Assignment not successful. TII LTI global id not set");
			}
			if (globalReportsId == null){
				throw new TransientSubmissionException("Create Assignment not successful. TII Reports LTI global id not set");
			}

			sakaiProps.setProperty(LTIService.LTI_SITE_ID,siteId);
			sakaiProps.setProperty(LTIService.LTI_TITLE,title);

			log.debug("Storing custom params: " + custom);
			sakaiProps.setProperty(LTIService.LTI_CUSTOM,custom);

			SecurityAdvisor advisor = new SimpleSecurityAdvisor(sessionManager.getCurrentSessionUserId(), "site.upd", "/site/!admin");
			Object ltiContent = null;
			Object ltiReportsContent = null;
			try{
				securityService.pushAdvisor(advisor);
				sakaiProps.setProperty(LTIService.LTI_TOOL_ID, globalId);
				if(StringUtils.isEmpty(ltiId)){
					ltiContent = tiiUtil.insertTIIToolContent(globalId, sakaiProps);
				} else {//don't create lti tool if exists
					ltiContent = tiiUtil.updateTIIToolContent(ltiId, sakaiProps);
				}				
				// replace the property
				sakaiProps.setProperty(LTIService.LTI_TOOL_ID, globalReportsId);
				if (StringUtils.isEmpty(ltiReportsId))
				{
					ltiReportsContent = tiiUtil.insertTIIToolContent(globalReportsId, sakaiProps);
				}
				else
				{
					ltiReportsContent = tiiUtil.updateTIIToolContent(ltiReportsId, sakaiProps);
				}
			} catch(Exception e){
				throw new TransientSubmissionException("Create Assignment not successful. Error trying to insert TII tool content: " + e.getMessage());
			} finally {
				securityService.popAdvisor(advisor);
			}
				
			if(ltiContent == null){
				throw new TransientSubmissionException("Create Assignment not successful. Could not create LTI tool for the task: " + custom);

			} else if (ltiReportsContent == null){
				throw new TransientSubmissionException("Create Assignment not successful. Could not create LTI Reports tool for the task: " + custom);
			} else if (!StringUtils.isEmpty(ltiId) && !Boolean.TRUE.equals(ltiContent)){
				// if ltiId is not empty, the lti already exists, so we did an update. ltiContent is Boolean.TRUE if the update was successful
				throw new TransientSubmissionException("Update Assignment not successful. Error updating LTI stealthed tool: " + ltiId);
			} else if (ltiReportsId != null && !Boolean.TRUE.equals(ltiReportsContent)){
				throw new TransientSubmissionException("Update Assignment not successful. Error updating LTI reports stealthed tool: " + ltiReportsContent);
			} else if (StringUtils.isEmpty(ltiId) && !(ltiContent instanceof Long)){
				// if ltiId is empty, the lti is new, so we did an insert. ltiContent is a Long primary key if the update was successful
				throw new TransientSubmissionException("Create Assignment not successful. Error creating LTI stealthed tool: " + ltiContent);
			} else if (ltiReportsId == null && !(ltiReportsContent instanceof Long)){
				throw new TransientSubmissionException("Create Assignment not successful. Error creating LTI stealthed tool: " + ltiReportsContent);
			}
			if (StringUtils.isEmpty(ltiId) || ltiReportsId == null) {//we inserted, need to record the IDs
				log.debug("LTI content tool id: " + ltiContent);
				try{

					if (ltiReportsId == null)
					{
						ResourcePropertiesEdit rpe = s.getPropertiesEdit();
						rpe.addProperty("turnitin_reports_lti_id", String.valueOf(ltiReportsContent));
						siteService.save(s);
					}
				}
				catch (IdUnusedException e)
				{
					log.error("Could not store reports LTI tool ID " + ltiReportsContent + " for site " + s.getId(), e);
					throw new TransientSubmissionException("Create Assignment not successful. Error storing LTI stealthed reports tool: " + ltiReportsContent);
				}
				catch (PermissionException e)
				{
					log.error("Could not store reports LTI tool ID " + ltiReportsContent + " for site " + s.getId(), e);
					throw new TransientSubmissionException("Create Assignment not successful. Error storing LTI stealthed reports tool: " + ltiReportsContent);
				}

				boolean added = saveOrUpdateActivityConfigEntry(TurnitinConstants.STEALTHED_LTI_ID, String.valueOf(ltiContent), asnId, TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID,
						TurnitinConstants.PROVIDER_ID, true);
				if (!added)
				{
					log.error("Could not store LTI tool ID " + ltiContent + " for assignment " + taskId);
					throw new TransientSubmissionException("Create Assignment not successful. Error storing LTI stealthed tool: " + ltiContent);
				}
			}
			
			//add submissions to the queue if there is any
			try{
				log.debug("Adding previous submissions");
				//this will be done always - no problem, extra checks
				log.debug("Assignment " + asn.getId() + " - " + asn.getTitle());
				List<AssignmentSubmission> submissions = assignmentService.getSubmissions(asn);
				if(submissions != null){
					for(AssignmentSubmission sub : submissions){
						//if submitted
						if(sub.getSubmitted()){
							log.debug("Submission " + sub.getId());
							boolean allowAnyFile = asn.getContent().isAllowAnyFile();
							List<ContentResource> resources = getAllAcceptableAttachments(sub,allowAnyFile);

							// determine the owner of the submission for purposes of content review
							String ownerId = asn.isGroup() ? sub.getSubmittedForGroupByUserId() : sub.getSubmitterId();
							if (ownerId.isEmpty())
							{
								String msg = "Unable to submit content items to review service for submission %s to assignment %s. "
										+ "An appropriate owner for the submission cannot be determined.";
								log.warn(String.format(msg, sub.getId(), asn.getId()));
								continue;
							}

							for(ContentResource resource : resources){
								//if it wasnt added
								if(getFirstItemByContentId(resource.getId()) == null){
									log.debug("was not added");
									queueContent(ownerId, null, asn.getReference(), resource.getId(), sub.getId(), false);
								}
								//else - is there anything or any status we should check?
							}
						}
					}
				}	
			} catch(Exception e){
				log.warn("Error while tying to queue previous submissions.");
			}
			
			return;
		}
		
		//////////////////////////////  OLD API INTEGRATION  ///////////////////////////////
	
		//get the assignment reference
		String taskTitle = "";
		if(extraAsnnOpts.containsKey("title")){
			taskTitle = extraAsnnOpts.get("title").toString();
		}else{
			getAssignmentTitle(taskId);
		}
		log.debug("Creating assignment for site: " + siteId + ", task: " + taskId +" tasktitle: " + taskTitle);

		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat.getDateInstance());
		dform.applyPattern(TURNITIN_DATETIME_FORMAT);
		Calendar cal = Calendar.getInstance();
		//set this to yesterday so we avoid timezone problems etc
		//TII-143 seems this now causes problems may need a finner tweak than 1 day like midnight +1 min or something
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MINUTE, 1);
		//cal.add(Calendar.DAY_OF_MONTH, -1);
		String dtstart = dform.format(cal.getTime());
		String today = dtstart;

		//set the due dates for the assignments to be in 5 month's time
		//turnitin automatically sets each class end date to 6 months after it is created
		//the assignment end date must be on or before the class end date

		String fcmd = "2";                                            //new assignment
		boolean asnnExists = false;
		// If this assignment already exists, we should use fcmd 3 to update it.
		Map tiiresult = this.getAssignment(siteId, taskId);
		if (tiiresult.get("rcode") != null && tiiresult.get("rcode").equals("85")) {
		    fcmd = "3";
		    asnnExists = true;
		}

		/* Some notes about start and due dates. This information is
		 * accurate as of Nov 12, 2009 and was determined by testing
		 * and experimentation with some Sash scripts.
		 *
		 * A turnitin due date, must be after the start date. This makes
		 * sense and follows the logic in both Assignments 1 and 2.
		 *
		 * When *creating* a new Turnitin Assignment, the start date
		 * must be todays date or later.  The format for dates only
		 * includes the day, and not any specific times. I believe that,
		 * in order to make up for time zone differences between your
		 * location and the turnitin cloud, it can be basically the
		 * current day anywhere currently, with some slack. For instance
		 * I can create an assignment for yesterday, but not for 2 days
		 * ago. Doing so causes an error.
		 *
		 * However!  For an existing turnitin assignment, you appear to
		 * have the liberty of changing the start date to sometime in
		 * the past. You can also change an assignment to have a due
		 * date in the past as long as it is still after the start date.
		 *
		 * So, to avoid errors when syncing information, or adding
		 * turnitin support to new or existing assignments we will:
		 *
		 * 1. If the assignment already exists we'll just save it.
		 *
		 * 2. If the assignment does not exist, we will save it once using
		 * todays date for the start and due date, and then save it again with
		 * the proper dates to ensure we're all tidied up and in line.
		 *
		 * Also, with our current class creation, due dates can be 5
		 * years out, but not further. This seems a bit lower priortity,
		 * but we still should figure out an appropriate way to deal
		 * with it if it does happen.
		 *
		 */



		//TODO use the 'secret' function to change this to longer
		cal.add(Calendar.MONTH, 5);
		String dtdue = dform.format(cal.getTime());
		log.debug("Set date due to: " + dtdue);
		if (extraAsnnOpts != null && extraAsnnOpts.containsKey("dtdue")) {
			dtdue = extraAsnnOpts.get("dtdue").toString();
			log.debug("Settign date due from external to: " + dtdue);
			extraAsnnOpts.remove("dtdue");
		}

		String fid = "4";						//function id
		String utp = "2"; 					//user type 2 = instructor
		String s_view_report = "1";
		if (extraAsnnOpts != null && extraAsnnOpts.containsKey("s_view_report")) {
			s_view_report = extraAsnnOpts.get("s_view_report").toString();
			extraAsnnOpts.remove("s_view_report");
		}

		//erater
		String erater = (serverConfigurationService.getBoolean("turnitin.option.erater.default", false)) ? "1" : "0";
		String ets_handbook ="1";
		String ets_dictionary="en";
		String ets_spelling = "1";
		String ets_style = "1";
		String ets_grammar = "1";
		String ets_mechanics = "1";
		String ets_usage = "1";

		try{
			if (extraAsnnOpts != null && extraAsnnOpts.containsKey("erater")) {
				erater = extraAsnnOpts.get("erater").toString();
				extraAsnnOpts.remove("erater");

				ets_handbook = extraAsnnOpts.get("ets_handbook").toString();
				extraAsnnOpts.remove("ets_handbook");

				ets_dictionary = extraAsnnOpts.get("ets_dictionary").toString();
				extraAsnnOpts.remove("ets_dictionary");

				ets_spelling = extraAsnnOpts.get("ets_spelling").toString();
				extraAsnnOpts.remove("ets_spelling");

				ets_style = extraAsnnOpts.get("ets_style").toString();
				extraAsnnOpts.remove("ets_style");

				ets_grammar = extraAsnnOpts.get("ets_grammar").toString();
				extraAsnnOpts.remove("ets_grammar");

				ets_mechanics = extraAsnnOpts.get("ets_mechanics").toString();
				extraAsnnOpts.remove("ets_mechanics");

				ets_usage = extraAsnnOpts.get("ets_usage").toString();
				extraAsnnOpts.remove("ets_usage");
			}
		}catch(Exception e){
			log.info("(createAssignment)erater extraAsnnOpts. "+e);
		}

		String cid = siteId;
		String assignid = taskId;
		String ctl = siteId;

		Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
				"assign", taskTitle,
				"assignid", assignid,
				"cid", cid,
				"ctl", ctl,
				"dtdue", dtdue,
				"dtstart", dtstart,
				"fcmd", "3",
				"fid", fid,
				"s_view_report", s_view_report,
				"utp", utp,
				"erater",erater,
				"ets_handbook",ets_handbook,
				"ets_dictionary",ets_dictionary,
				"ets_spelling",ets_spelling,
				"ets_style",ets_style,
				"ets_grammar",ets_grammar,
				"ets_mechanics",ets_mechanics,
				"ets_usage",ets_usage
				);

		// Save instructorInfo up here to reuse for calls in this
		// method, since theoretically getInstructorInfo could return
		// different instructors for different invocations and we need
		// the same one since we're using a session id.
		Map instructorInfo = getInstructorInfo(siteId);
		params.putAll(instructorInfo);

		if (extraAsnnOpts != null) {
			for (Object key: extraAsnnOpts.keySet()) {
				if (extraAsnnOpts.get(key) == null) {
					continue;
				}
				params = TurnitinAPIUtil.packMap(params, key.toString(),
						extraAsnnOpts.get(key).toString());
			}
		}

		// We only need to use a session id if we are creating this
		// assignment for the first time.
		String sessionid = null;
		Map sessionParams = null;

		if (!asnnExists) {
			// Try adding the user in case they don't exist TII-XXX
			addTurnitinInstructor(instructorInfo);

			sessionParams = turnitinConn.getBaseTIIOptions();
			sessionParams.putAll(instructorInfo);
			sessionParams.put("utp", utp);
			sessionid = TurnitinSessionFuncs.getTurnitinSession(turnitinConn, sessionParams);

			Map firstparams = new HashMap();
			firstparams.putAll(params);
			firstparams.put("session-id", sessionid);
			firstparams.put("dtstart", today);

			// Make the due date in the future
			Calendar caldue = Calendar.getInstance();
			caldue.add(Calendar.MONTH, 5);
			String dtdue_first = dform.format(caldue.getTime());
			firstparams.put("dtdue", dtdue_first);

			log.debug("date due is: " + dtdue);
			log.debug("Start date: " + today);
			firstparams.put("fcmd", "2");
			Document firstSaveDocument =
				turnitinConn.callTurnitinReturnDocument(firstparams);
			Element root = firstSaveDocument.getDocumentElement();
			int rcode = new Integer(((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
			if ((rcode > 0 && rcode < 100) || rcode == 419) {
				log.debug("Create FirstDate Assignment successful");
				log.debug("tii returned " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
			} else {
				log.debug("FirstDate Assignment creation failed with message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
				//log.debug(root);
				throw new TransientSubmissionException("FirstDate Create Assignment not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode
						, rcode);
			}
		}
		log.debug("going to attempt second update");
		if (sessionid != null) {
		    params.put("session-id", sessionid);
		}
		Document document = turnitinConn.callTurnitinReturnDocument(params);

		Element root = document.getDocumentElement();
		int rcode = new Integer(((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
		if ((rcode > 0 && rcode < 100) || rcode == 419) {
			log.debug("Create Assignment successful");
			log.debug("tii returned " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
		} else {
			log.debug("Assignment creation failed with message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
			//log.debug(root);
			throw new TransientSubmissionException("Create Assignment not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode
					, rcode);
		}

		if (sessionid != null) {
		    TurnitinSessionFuncs.logoutTurnitinSession(turnitinConn, sessionid, sessionParams);
		}
	}

	/**
	 * TII-245
	 * Handles individual extensions
	 * After Turnitin's due date, it will only accept a paper if one has not already been submitted.
	 * When "Select User(s)and Allow Resubmission" is used, we have to push the due date back on the TII end to accommodate multiple submissions
	 */
	private void handleIndividualExtension(Date extensionDate, String taskId, Map<String, Object> extraAsnOpts)
	{
		// Get the latest offered extenion.
		// We keep track of this in the activity config table to save us from querying every submission to find the latest extension.
		// This comes at the cost that we can never move TII's due date earlier once we've granted an extension; we can only push it out
		String strLatestExtensionDate = getActivityConfigValue(TurnitinConstants.TURNITIN_ASN_LATEST_INDIVIDUAL_EXTENSION_DATE, TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID, taskId, TurnitinConstants.PROVIDER_ID);
		if (extensionDate != null || !StringUtils.isBlank(strLatestExtensionDate))
		{
			// The due date we're sending to TII (latest of accept until / resubmit accept until)
			long timestampDue = (Long) extraAsnOpts.get("timestampDue");
			try
			{
				// Find what's later: the new extension date or the latest existing extension date
				long latestExtensionDate = 0;
				if (!StringUtils.isBlank(strLatestExtensionDate))
				{
					latestExtensionDate = Long.parseLong(strLatestExtensionDate);
				}
				if (extensionDate != null)
				{
					// We are offering a student an individual extension, handle if it's later than the current latest extension date
					long lExtensionDate = extensionDate.getTime();
					if (lExtensionDate > latestExtensionDate)
					{
						// we have a new latest extension date
						saveOrUpdateActivityConfigEntry(TurnitinConstants.TURNITIN_ASN_LATEST_INDIVIDUAL_EXTENSION_DATE, String.valueOf(lExtensionDate), TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID, taskId, TurnitinConstants.PROVIDER_ID, true);
						latestExtensionDate = lExtensionDate;
					}
				}

				// Push Turnitin's due date back if we need to accommodate an extension later than the due date
				if (latestExtensionDate > timestampDue)
				{
					// push the due date to the latest extension date
					extraAsnOpts.put("timestampDue", Long.valueOf(latestExtensionDate));
				}
			}
			catch (NumberFormatException nfe)
			{
				log.warn("NumberFormatException thrown when parsing either the latest extension date: " + strLatestExtensionDate + ", or the timestampDue option: " + timestampDue);
			}
		}
	}


	/**
	 * Currently public for integration tests. TODO Revisit visibility of
	 * method.
	 *
	 * @param userId
	 * @param uem
	 * @param siteId
	 * @throws SubmissionException
	 * @throws org.sakaiproject.contentreview.exception.TransientSubmissionException
	 */
	public void enrollInClass(String userId, String uem, String siteId) throws SubmissionException, TransientSubmissionException {

		String uid = userId;
		String cid = siteId;

		String ctl = siteId;
		String fid = "3";
		String fcmd = "2";
		String tem = getTEM(cid);

		User user;
		try {
			user = userDirectoryService.getUser(userId);
		} catch (Exception t) {
			throw new SubmissionException ("Cannot get user information", t);
		}

		log.debug("Enrolling user " + user.getEid() + "(" + userId + ")  in class " + siteId);

		String ufn = getUserFirstName(user);
		if (ufn == null) {
			throw new SubmissionException ("User has no first name");
		}

		String uln = getUserLastName(user);
		if (uln == null) {
			throw new SubmissionException ("User has no last name");
		}

		String utp = "1";

		Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
				"fid", fid,
				"fcmd", fcmd,
				"cid", cid,
				"tem", tem,
				"ctl", ctl,
				"dis", studentAccountNotified ? "0" : "1",
				"uem", uem,
				"ufn", ufn,
				"uln", uln,
				"utp", utp,
				"uid", uid
		);

		Document document = turnitinConn.callTurnitinReturnDocument(params);

		Element root = document.getDocumentElement();

		String rMessage = ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData();
		String rCode = ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData();
		if ("31".equals(rCode)) {
			log.debug("Results from enrollInClass with user + " + userId + " and class title: " + ctl + ".\n" +
					"rCode: " + rCode + " rMessage: " + rMessage);
		} else {
			//certain return codes need to be logged
			log.warn("Results from enrollInClass with user + " + userId + " and class title: " + ctl + ". " +
					"rCode: " + rCode + ", rMessage: " + rMessage);
			//TODO for certain types we should probably throw an exception here and stop the proccess
		}

	}

	/*
	 * Obtain a lock on the item
	 */
	private boolean obtainLock(String itemId) {
		Boolean lock = dao.obtainLock(itemId, serverConfigurationService.getServerId(), LOCK_PERIOD);
		return (lock != null) ? lock : false;
	}

	/*
	 * Get the next item that needs to be submitted
	 *
	 */
	private ContentReviewItem getNextItemInSubmissionQueue() {

		// Submit items that haven't yet been submitted
		Search search = new Search();
		search.addRestriction(new Restriction("status", ContentReviewItem.NOT_SUBMITTED_CODE));
		List<ContentReviewItem> notSubmittedItems = dao.findBySearch(ContentReviewItem.class, search);
		for( ContentReviewItem item : notSubmittedItems ) {

			// can we get a lock?
			if (obtainLock("item." + item.getId().toString())) {
				return item;
			}
		}

		// Submit items that should be retried
		search = new Search();
		search.addRestriction(new Restriction("status", ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE));
		notSubmittedItems = dao.findBySearch(ContentReviewItem.class, search);
		ContentReviewItem nextItem = getNotSubmittedItemPastRetryTime( notSubmittedItems );
		if( nextItem != null )
		{
			return nextItem;
		}

		// submit items that are awaiting reports, but the externalId is null (Ie. they've been submitted, but the callback to set the externalId failed).
		search = new Search();
		search.addRestriction(new Restriction("status", ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE));
		search.addRestriction(new Restriction("externalId", "", Restriction.NULL));
		notSubmittedItems = dao.findBySearch(ContentReviewItem.class, search);
		nextItem = getNotSubmittedItemPastRetryTime(notSubmittedItems);
		if (nextItem != null)
		{
			return nextItem;
		}

		// submit items that are awaiting reports in an errory_retry state, and the externalId is null (similar to above condition, just happens to be in an errory_retry state)
		search = new Search();
		search.addRestriction(new Restriction("status", ContentReviewItem.REPORT_ERROR_RETRY_CODE));
		search.addRestriction(new Restriction("externalId", "", Restriction.NULL));
		notSubmittedItems = dao.findBySearch(ContentReviewItem.class, search);
		nextItem = getNotSubmittedItemPastRetryTime(notSubmittedItems);
		if (nextItem != null)
		{
			return nextItem;
		}

		// Submit items that were previously marked as missing submitter details (first name, last name, email)
		search = new Search();
		search.addRestriction( new Restriction( "status", ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE ) );
		notSubmittedItems = dao.findBySearch( ContentReviewItem.class, search );
		nextItem = getNotSubmittedItemPastRetryTime( notSubmittedItems );

		// At this point, nextItem could be null (indicating the submission queue is empty)
		return nextItem;
	}

	/**
	 * Returns the first item in the list which has surpassed it's next retry time, and we can get a lock on the object.
	 * Otherwise returns null.
	 * 
	 * @param notSubmittedItems the list of ContentReviewItems to iterate over.
	 * @return the first item in the list that meets the requirements, or null.
	 */
	private ContentReviewItem getNotSubmittedItemPastRetryTime( List<ContentReviewItem> notSubmittedItems )
	{
		for( ContentReviewItem item : notSubmittedItems )
		{
			if( hasReachedRetryTime( item ) && obtainLock( "item." + item.getId().toString() ) )
			{
				return item;
			}
		}

		return null;
	}

	private boolean hasReachedRetryTime(ContentReviewItem item) {

		// has the item reached its next retry time?
		if (item.getNextRetryTime() == null)
		{
			item.setNextRetryTime(new Date());
		}

		if (item.getNextRetryTime().after(new Date())) {
			//we haven't reached the next retry time
			log.info("next retry time not yet reached for item: " + item.getId());
			dao.update(item);
			return false;
		}

		return true;

	}

	private void releaseLock(ContentReviewItem currentItem) {
		dao.releaseLock("item." + currentItem.getId().toString(), serverConfigurationService.getServerId());
	}

	public void processQueue() {

		log.info("Processing submission queue");
		int errors = 0;
		int success = 0;


		for (ContentReviewItem currentItem = getNextItemInSubmissionQueue(); currentItem != null; currentItem = getNextItemInSubmissionQueue()) {

			log.debug("Attempting to submit content: " + currentItem.getContentId() + " for user: " + currentItem.getUserId() + " and site: " + currentItem.getSiteId());

			if (currentItem.getRetryCount() == null ) {
				currentItem.setRetryCount(Long.valueOf(0));
				currentItem.setNextRetryTime(this.getNextRetryTime(0));
				dao.update(currentItem);
			} else if (currentItem.getRetryCount().intValue() > maxRetry) {
				processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_EXCEEDED, null, null );
				errors++;
				continue;
			} else {
				long l = currentItem.getRetryCount();
				l++;
				currentItem.setRetryCount(l);
				currentItem.setNextRetryTime(this.getNextRetryTime(l));
				dao.update(currentItem);
			}

			User user;

			try {
				user = userDirectoryService.getUser(currentItem.getUserId());
			} catch (UserNotDefinedException e1) {
				log.error("Submission attempt unsuccessful - User not found.", e1);
				processError(currentItem, ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE, null, null);
				errors++;
				continue;
			}


			String uem = getEmail(user);
			if (uem == null ){
				if( currentItem.getRetryCount() == 0 )
				{
					log.error("User: " + user.getEid() + " has no valid email");
				}
				processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE, "no valid email", null );
				errors++;
				continue;
			}

			String ufn = getUserFirstName(user);
			if (ufn == null || ufn.equals("")) {
				if( currentItem.getRetryCount() == 0 )
				{
					log.error("Submission attempt unsuccessful - User has no first name");
				}
				processError(currentItem, ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE, "has no first name", null);
				errors++;
				continue;
			}

			String uln = getUserLastName(user);
			if (uln == null || uln.equals("")) {
				if( currentItem.getRetryCount() == 0 )
				{
					log.error("Submission attempt unsuccessful - User has no last name");
				}
				processError(currentItem, ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE, "has no last name", null);
				errors++;
				continue;
			}
			
			Site s;
			try {
				s = siteService.getSite(currentItem.getSiteId());
			}
			catch (IdUnusedException iue) {
				log.error("processQueue: Site " + currentItem.getSiteId() + " not found!" + iue.getMessage());
				processError(currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE, "site not found", null);
				errors++;
				continue;
			}
			
			//to get the name of the initial submited file we need the title
			ContentResource resource;
			ResourceProperties resourceProperties;
			String fileName;
			try {
				try {
					resource = contentHostingService.getResource(currentItem.getContentId());
				} catch (IdUnusedException e4) {
					// Remove this item
					log.warn("IdUnusedException: no resource with id " + currentItem.getContentId());
					dao.delete(currentItem);
					releaseLock(currentItem);
					errors++;
					continue;
				}
				resourceProperties = resource.getProperties();
				fileName = resourceProperties.getProperty(resourceProperties.getNamePropDisplayName());
				fileName = escapeFileName(fileName, resource.getId());
			}
			catch (PermissionException e2) {
				log.error("Submission failed due to permission error.", e2);
				processError(currentItem, ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE, "permission exception", null);
				errors++;
				continue;
			}
			catch (TypeException e) {
				log.error("Submission failed due to content Type error.", e);
				processError(currentItem, ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE, "Type Exception: " + e.getMessage(), null);
				errors++;
				continue;
			}

			//TII-97 filenames can't be longer than 200 chars
			if (fileName != null && fileName.length() >=200 ) {
				fileName = truncateFileName(fileName, 198);
			}
		
			//////////////////////////////  NEW LTI INTEGRATION  ///////////////////////////////
			Optional<Date> dateOpt = getAssignmentCreationDate(currentItem.getTaskId());
			if(dateOpt.isPresent() && siteAdvisor.siteCanUseLTIReviewServiceForAssignment(s, dateOpt.get()) && currentItem.getSubmissionId()!=null){
				
				Map<String,String> ltiProps = new HashMap<>();
				ltiProps.put("context_id", currentItem.getSiteId());
				ltiProps.put("resource_link_id", currentItem.getTaskId());
				ltiProps.put("roles", "Learner");
				//student
				ltiProps.put("lis_person_name_family", uln);
				ltiProps.put("lis_person_contact_email_primary", uem);
				ltiProps.put("lis_person_name_full", ufn + " " + uln);
				ltiProps.put("lis_person_name_given", ufn);
				ltiProps.put("user_id", currentItem.getUserId());

				String[] parts = currentItem.getTaskId().split("/");
				log.debug(parts[parts.length -1] + " " + parts.length);
				String httpAccess = serverConfigurationService.getServerUrl() + "/access/assignment/s/" + currentItem.getSiteId() + "/" + parts[parts.length -1] + "/" + currentItem.getSubmissionId();
				httpAccess += ":" + currentItem.getId() + ":" + currentItem.getContentId().hashCode();
				log.debug("httpAccess url: " + httpAccess);//debug
				ltiProps.put("submission_url", httpAccess);
				ltiProps.put("submission_title", fileName);
				// must have an extension or they can't process it
				if (fileName.equals("Inline_Submission")) {
					fileName = "Inline_Submission.html";
				}
				ltiProps.put("submission_filename", fileName);
				ltiProps.put("ext_outcomes_tool_placement_url", serverConfigurationService.getServerUrl() + "/sakai-contentreview-tool-tii/submission-servlet");
				ltiProps.put("lis_outcome_service_url", serverConfigurationService.getServerUrl() + "/sakai-contentreview-tool-tii/grading-servlet");
				ltiProps.put("lis_result_sourcedid", currentItem.getContentId());
				ltiProps.put("xmlresponse","1");//mandatatory
				
				org.sakaiproject.assignment.api.Assignment a;
				try
				{
					a = assignmentService.getAssignment(currentItem.getTaskId());
				}
				catch (IdUnusedException e)
				{
					log.error("IdUnusedException: no assignment with id: " + currentItem.getTaskId(), e);
					a = null;
				}
				catch (PermissionException e)
				{
					log.error("PermissionException: no permission for assignment with id: " + currentItem.getTaskId(), e);
					a = null;
				}

				String tiiId = "";
				if (a != null)
				{
					tiiId = getActivityConfigValue(TurnitinConstants.TURNITIN_ASN_ID, a.getId(), TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID,
						TurnitinConstants.PROVIDER_ID);
				}

				if(tiiId.isEmpty()){
					log.error("Could not find tiiId for assignment: " + currentItem.getTaskId());
					processError(currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE, "Could not find tiiId", null);
					errors++;
					continue;
				}
				
				TurnitinReturnValue result = new TurnitinReturnValue();
				result.setResult( -1 );
				boolean isResubmission = false;
				if(currentItem.isResubmission())
				{
					AssignmentContent ac = a.getContent();
					// Resubmit only for submission types that allow only one file per submission
					String tiiPaperId = currentItem.getExternalId();
					// 1 - inline, 2 - attach, 3 - both, 4 - non elec, 5 - single file
					int type = ac.getTypeOfSubmission();
					if (tiiPaperId != null && (type == 1 || type == 5))
					{
						isResubmission = true;
						result = tiiUtil.makeLTIcall(TurnitinLTIUtil.RESUBMIT, tiiPaperId, ltiProps);
					}
				}
				if (!isResubmission)
				{
					result = tiiUtil.makeLTIcall(TurnitinLTIUtil.SUBMIT, tiiId, ltiProps);
				}

				if(result.getResult() >= 0){
					log.debug("LTI submission successful");
					//problems overriding this on callback
					//currentItem.setExternalId(externalId);
					currentItem.setStatus(ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE);
					currentItem.setRetryCount(Long.valueOf(0));
					currentItem.setLastError(null);
					currentItem.setErrorCode(null);
					currentItem.setDateSubmitted(new Date());
					success++;
					dao.update(currentItem);
					releaseLock(currentItem);
				} else {
					Long errorCode = ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE;
					// TII-242 - evaluate result.getErrorMessage() to prevent unnecessary retries if the error is terminal
					if (terminalQueueErrors.contains(result.getErrorMessage()))
					{
						errorCode = ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE;
					}
					else
					{
						long l = currentItem.getRetryCount();
						l++;
						currentItem.setRetryCount(l);
						currentItem.setNextRetryTime(this.getNextRetryTime(l));
					}
					processError( currentItem, errorCode, "Submission Error: " + result.getErrorMessage(), null );
					errors++;
				}

				continue;
			}
			
			//////////////////////////////  OLD API INTEGRATION  ///////////////////////////////

			if (!turnitinConn.isUseSourceParameter()) {
				try {
					createClass(currentItem.getSiteId());
				} catch (SubmissionException t) {
					log.error ("Submission attempt unsuccessful: Could not create class", t);
					processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE, "Class creation error: " + t.getMessage(), null );
					errors++;
					continue;
				} catch (TransientSubmissionException tse) {
					processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE, "Class creation error: " + tse.getMessage(), null );
					errors++;
					continue;
				}
			}

			try {
				enrollInClass(currentItem.getUserId(), uem, currentItem.getSiteId());
			} catch (Exception t) {
				log.error("Submission attempt unsuccessful: Could not enroll user in class", t);

				Long status;
				String error;
				if (t.getClass() == IOException.class) {
					error = "Enrolment error: " + t.getMessage();
					status = ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE;
				} else {
					error = "Enrolment error: " + t.getMessage();
					status = ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE;
				}

				processError( currentItem, status, error, null );
				errors++;
				continue;
			}

			if (!turnitinConn.isUseSourceParameter()) {
				try {
					Map tiiresult = this.getAssignment(currentItem.getSiteId(), currentItem.getTaskId());
					if (tiiresult.get("rcode") != null && !tiiresult.get("rcode").equals("85")) {
						createAssignment(currentItem.getSiteId(), currentItem.getTaskId());
					}
				} catch (SubmissionException se) {
					processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE, "Assignment creation error: " + se.getMessage(), se.getErrorCode() );
					errors++;
					continue;
				} catch (TransientSubmissionException tse) {
					if (tse.getErrorCode() != null) {
						currentItem.setErrorCode(tse.getErrorCode());
					}

					processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE, "Assignment creation error: " + tse.getMessage(), null );
					errors++;
					continue;

				}
			}

			//get all the info for the api call
			//we do this before connecting so that if there is a problem we can jump out - saves time
			//these errors should probably be caught when a student is enrolled in a class
			//but we check again here to be sure

			String fcmd = "2";
			String fid = "5";

			String userEid = currentItem.getUserId();
			try {
				userEid = userDirectoryService.getUserEid(currentItem.getUserId());
			}
			catch (UserNotDefinedException unde) {
				//nothing realy to do?
			}

			String ptl =  userEid  + ":" + fileName;
			String ptype = "2";

			String uid = currentItem.getUserId();
			String cid = currentItem.getSiteId();
			String assignid = currentItem.getTaskId();

			// TODO ONC-1292 How to get this, and is it still required with src=9?
			String tem = getTEM(cid);

			String utp = "1";

			log.debug("Using Emails: tem: " + tem + " uem: " + uem);

			String assign = getAssignmentTitle(currentItem.getTaskId());
			String ctl = currentItem.getSiteId();

			Map params = TurnitinAPIUtil.packMap( turnitinConn.getBaseTIIOptions(),
					"assignid", assignid,
					"uid", uid,
					"cid", cid,
					"assign", assign,
					"ctl", ctl,
					"dis", sendSubmissionNotification > 0 ? "0" : "1", // dis=1 means disable sending email
					"fcmd", fcmd,
					"fid", fid,
					"ptype", ptype,
					"ptl", ptl,
					"tem", tem,
					"uem", uem,
					"ufn", ufn,
					"uln", uln,
					"utp", utp,
					"resource_obj", resource
			);

			Document document;
			try {
				document = turnitinConn.callTurnitinReturnDocument(params, true);
			}
			catch (TransientSubmissionException | SubmissionException e) {
				processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE, "Error Submitting Assignment for Submission: " + e.getMessage() + ". Assume unsuccessful", null );
				errors++;
				continue;
			}

			Element root = document.getDocumentElement();

			String rMessage = ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData();
			String rCode = ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData();

			if (rCode == null)
			{
				rCode = "";
			}
			else
			{
				rCode = rCode.trim();
			}

			if (rMessage == null)
			{
				rMessage = rCode;
			}
			else
			{
				rMessage = rMessage.trim();
			}

			if (rCode.compareTo("51") == 0) {
				String externalId = ((CharacterData) (root.getElementsByTagName("objectID").item(0).getFirstChild())).getData().trim();
				if (externalId != null && externalId.length() >0 ) {
					log.debug("Submission successful");
					currentItem.setExternalId(externalId);
					currentItem.setStatus(ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE);
					currentItem.setRetryCount(Long.valueOf(0));
					currentItem.setLastError(null);
					currentItem.setErrorCode(null);
					currentItem.setDateSubmitted(new Date());
					success++;
					dao.update(currentItem);
					dao.updateExternalId(currentItem.getContentId(), externalId);
					releaseLock( currentItem );
				} else {
					log.warn("invalid external id");
					processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE, "Submission error: no external id received", null );
					errors++;
				}
			} else {
				log.debug("Submission not successful: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim());

				Long status;
				if (rMessage.equals("User password does not match user email")
						|| "1001".equals(rCode) || "".equals(rMessage) || "413".equals(rCode) || "1025".equals(rCode) || "250".equals(rCode)) {
					status = ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE;
					log.warn("Submission not successful. It will be retried.");
				} else if (rCode.equals("423")) {
					status = ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE;
				} else if (rCode.equals("301")) {
					//this took a long time
					log.warn("Submission not successful due to timeout. It will be retried.");
					status = ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE;
					Calendar cal = Calendar.getInstance();
					cal.set(Calendar.HOUR_OF_DAY, 22);
					currentItem.setNextRetryTime(cal.getTime());
				}else {
					log.error("Submission not successful. It will NOT be retried.");
					status = ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE;
				}

				processError( currentItem, status, "Submission Error: " + rMessage + "(" + rCode + ")", Integer.valueOf(rCode) );
				errors++;
			}
		}

		log.info("Submission queue run completed: " + success + " items submitted, " + errors + " errors.");
	}

	public String escapeFileName(String fileName, String contentId) {
		log.debug("original filename is: " + fileName);
		if (fileName == null) {
			//use the id
			fileName  = contentId;
		} else if (fileName.length() > 199) {
			fileName = fileName.substring(0, 199);
		}
		log.debug("fileName is :" + fileName);
		try {
			fileName = URLDecoder.decode(fileName, "UTF-8");
			//in rare cases it seems filenames can be double encoded
			while (fileName.indexOf("%20")> 0 || fileName.contains("%2520") ) {
				try {
					fileName = URLDecoder.decode(fileName, "UTF-8");
				}
				catch (IllegalArgumentException eae) {
					log.warn("Unable to decode fileName: " + fileName, eae);
					//as the result is likely to cause a MD5 exception use the ID
					return contentId;
				}
			}
		}
		catch (IllegalArgumentException eae) {
			log.warn("Unable to decode fileName: " + fileName, eae);
			return contentId;
		} catch (UnsupportedEncodingException e) {
			log.debug( e );
		}

		fileName = fileName.replace(' ', '_');
		//its possible we have double _ as a result of this lets do some cleanup
		fileName = StringUtils.replace(fileName, "__", "_");

		log.debug("fileName is :" + fileName);
		return fileName;
	}

	private String truncateFileName(String fileName, int i) {
		//get the extension for later re-use
		String extension = "";
		if (fileName.contains(".")) {
			 extension = fileName.substring(fileName.lastIndexOf("."));
		}

		fileName = fileName.substring(0, i - extension.length());
		fileName = fileName + extension;

		return fileName;
	}

	public void checkForReports() {
		checkForReportsBulk();
	}

	/*
	 * Fetch reports on a class by class basis
	 */
	@SuppressWarnings({ "deprecation", "unchecked" })
	public void checkForReportsBulk() {

		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat.getDateInstance());
		dform.applyPattern(TURNITIN_DATETIME_FORMAT);

		log.info("Fetching reports from Turnitin");

		// get the list of all items that are waiting for reports
		// but skip items with externalId = null, this happens when the LTI integration's callback fails. In this case, they'll be resubmitted by the queue job.
		// For the Sakai API integration, we should never enter the report state with externalId = null
		List<ContentReviewItem> awaitingReport = dao.findByProperties(ContentReviewItem.class,
				new String[] { "status", "externalId" },
				new Object[] { ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE, "" },
				new int[] { dao.EQUALS, dao.NOT_NULL });

		awaitingReport.addAll(dao.findByProperties(ContentReviewItem.class,
				new String[] { "status", "externalId" },
				new Object[] { ContentReviewItem.REPORT_ERROR_RETRY_CODE, "" },
				new int[] { dao.EQUALS, dao.NOT_NULL }));

		Iterator<ContentReviewItem> listIterator = awaitingReport.iterator();
		HashMap<String, Integer> reportTable = new HashMap<>();

		log.debug("There are " + awaitingReport.size() + " submissions awaiting reports");

		ContentReviewItem currentItem;
		while (listIterator.hasNext()) {
			currentItem = (ContentReviewItem) listIterator.next();

			try
			{
				// has the item reached its next retry time?
				if (currentItem.getNextRetryTime() == null)
					currentItem.setNextRetryTime(new Date());

				else if (currentItem.getNextRetryTime().after(new Date())) {
					//we haven't reached the next retry time
					log.info("next retry time not yet reached for item: " + currentItem.getId());
					dao.update(currentItem);
					continue;
				}

				if (currentItem.getRetryCount() == null ) {
					currentItem.setRetryCount(Long.valueOf(0));
					currentItem.setNextRetryTime(this.getNextRetryTime(0));
				} else if (currentItem.getRetryCount().intValue() > maxRetry) {
					processError( currentItem, ContentReviewItem.SUBMISSION_ERROR_RETRY_EXCEEDED, null, null );
					continue;
				} else {
					log.debug("Still have retries left, continuing. ItemID: " + currentItem.getId());
					// Moving down to check for report generate speed.
					//long l = currentItem.getRetryCount().longValue();
					//l++;
					//currentItem.setRetryCount(Long.valueOf(l));
					//currentItem.setNextRetryTime(this.getNextRetryTime(Long.valueOf(l)));
					//dao.update(currentItem);
				}
				
				Site s;
				try {
					s = siteService.getSite(currentItem.getSiteId());
				}
				catch (IdUnusedException iue) {
					log.warn("checkForReportsBulk: Site " + currentItem.getSiteId() + " not found!" + iue.getMessage());
					long l = currentItem.getRetryCount();
					l++;
					currentItem.setRetryCount(l);
					currentItem.setNextRetryTime(this.getNextRetryTime(l));
					currentItem.setLastError("Site not found");
					dao.update(currentItem);
					continue;
				}
				//////////////////////////////  NEW LTI INTEGRATION  ///////////////////////////////
				Optional<Date> dateOpt = getAssignmentCreationDate(currentItem.getTaskId());
				if(dateOpt.isPresent() && siteAdvisor.siteCanUseLTIReviewServiceForAssignment(s, dateOpt.get())){			
					log.debug("getReviewScore using the LTI integration");			
					
					Map<String,String> ltiProps = new HashMap<> ();
					ltiProps = putInstructorInfo(ltiProps, currentItem.getSiteId());
					
					String paperId = currentItem.getExternalId();
					
					if(paperId == null){
						log.warn("Could not find TII paper id for the content " + currentItem.getContentId());
						long l = currentItem.getRetryCount();
						l++;
						currentItem.setRetryCount(l);
						currentItem.setNextRetryTime(this.getNextRetryTime(l));
						currentItem.setLastError("Could not find TII paper id for the submission");
						dao.update(currentItem);
						continue;
					}
					
					TurnitinReturnValue result = tiiUtil.makeLTIcall(TurnitinLTIUtil.INFO_SUBMISSION, paperId, ltiProps);
					if(result.getResult() >= 0){
						currentItem.setReviewScore(result.getResult());
						currentItem.setStatus(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE);
						currentItem.setDateReportReceived(new Date());
						currentItem.setLastError(null);
						currentItem.setErrorCode(null);
						dao.update(currentItem);

						try
						{
							ContentResource resource = contentHostingService.getResource( currentItem.getContentId() );
							boolean itemUpdated = updateItemAccess( resource.getId() );
							if( !itemUpdated )
							{
								log.error( "Could not update cr item access status" );
							}
						}
						catch( PermissionException | IdUnusedException | TypeException ex )
						{
							log.error( "Could not update cr item access status", ex );
						}

						//log.debug("new report received: " + currentItem.getExternalId() + " -> " + currentItem.getReviewScore());
						log.debug("new report received: " + paperId + " -> " + currentItem.getReviewScore());
					} else {
						if(result.getResult() == -7){
							log.debug("report is still pending for paper " + paperId);
							currentItem.setStatus(ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE);
							currentItem.setLastError( result.getErrorMessage() );
							currentItem.setErrorCode( result.getResult() );
						} else {
							log.error("Error making LTI call");
							long l = currentItem.getRetryCount();
							l++;
							currentItem.setRetryCount(l);
							currentItem.setNextRetryTime(this.getNextRetryTime(l));
							currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
							currentItem.setLastError("Report Data Error: " + result.getResult());
						}
						dao.update(currentItem);
					}
					
					continue;
				}
				//////////////////////////////  OLD API INTEGRATION  ///////////////////////////////

				if (currentItem.getExternalId() == null || currentItem.getExternalId().equals("")) {
					currentItem.setStatus(Long.valueOf(4));
					dao.update(currentItem);
					continue;
				}

				if (!reportTable.containsKey(currentItem.getExternalId())) {
					// get the list from turnitin and see if the review is available

					log.debug("Attempting to update hashtable with reports for site " + currentItem.getSiteId());

					String fcmd = "2";
					String fid = "10";

					try {
						User user = userDirectoryService.getUser(currentItem.getUserId());
					} catch (Exception e) {
						log.error("Unable to look up user: " + currentItem.getUserId() + " for contentItem: " + currentItem.getId(), e);
					}

					String cid = currentItem.getSiteId();
					String tem = getTEM(cid);

					String utp = "2";

					String assignid = currentItem.getTaskId();

					String assign = currentItem.getTaskId();
					String ctl = currentItem.getSiteId();

					// TODO FIXME Current sgithens
					// Move the update setRetryAttempts to here, and first call and
					// check the assignment from TII to see if the generate until
					// due is enabled. In that case we don't want to waste retry
					// attempts and should just continue.
					try {
						// TODO FIXME This is broken at the moment because we need
						// to have a userid, but this is assuming it's coming from
						// the thread, but we're in a quartz job.
						//Map curasnn = getAssignment(currentItem.getSiteId(), currentItem.getTaskId());
						// TODO FIXME Parameterize getAssignment method to take user information
						Map getAsnnParams = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
								"assign", getAssignmentTitle(currentItem.getTaskId()), "assignid", currentItem.getTaskId(), "cid", currentItem.getSiteId(), "ctl", currentItem.getSiteId(),
								"fcmd", "7", "fid", "4", "utp", "2" );

						getAsnnParams.putAll(getInstructorInfo(currentItem.getSiteId()));

						Map curasnn = turnitinConn.callTurnitinReturnMap(getAsnnParams);

						if (curasnn.containsKey("object")) {
							Map curasnnobj = (Map) curasnn.get("object");
							String reportGenSpeed = (String) curasnnobj.get("generate");
							String duedate = (String) curasnnobj.get("dtdue");
							SimpleDateFormat retform = ((SimpleDateFormat) DateFormat.getDateInstance());
							retform.applyPattern(TURNITIN_DATETIME_FORMAT);
							Date duedateObj = null;
							try {
								if (duedate != null) {
									duedateObj = retform.parse(duedate);
								}
							} catch (ParseException pe) {
								log.warn("Unable to parse turnitin dtdue: " + duedate, pe);
							}
							if (reportGenSpeed != null && duedateObj != null &&
								reportGenSpeed.equals("2") && duedateObj.after(new Date())) {
								log.info("Report generate speed is 2, skipping for now. ItemID: " + currentItem.getId());
								// If there was previously a transient error for this item, reset the status
								if (ContentReviewItem.REPORT_ERROR_RETRY_CODE.equals(currentItem.getStatus())) {
									currentItem.setStatus(ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE);
									currentItem.setLastError(null);
									currentItem.setErrorCode(null);
									dao.update(currentItem);
								}
								continue;
							}
							else {
								log.debug("Incrementing retry count for currentItem: " + currentItem.getId());
								long l = currentItem.getRetryCount();
								l++;
								currentItem.setRetryCount(l);
								currentItem.setNextRetryTime(this.getNextRetryTime(l));
								dao.update(currentItem);
							}
						}
					} catch (SubmissionException | TransientSubmissionException e) {
						log.error("Unable to check the report gen speed of the asnn for item: " + currentItem.getId(), e);
					}

					Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
								"fid", fid,
								"fcmd", fcmd,
								"tem", tem,
								"assign", assign,
								"assignid", assignid,
								"cid", cid,
								"ctl", ctl,
								"utp", utp
						);
						params.putAll(getInstructorInfo(currentItem.getSiteId()));

					Document document;

					try {
						document = turnitinConn.callTurnitinReturnDocument(params);
					}
					catch (TransientSubmissionException e) {
						log.warn("Update failed due to TransientSubmissionException error: " + e.toString(), e);
						currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
						currentItem.setLastError(e.getMessage());
						dao.update(currentItem);
						break;
					}
					catch (SubmissionException e) {
						log.warn("Update failed due to SubmissionException error: " + e.toString(), e);
						currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
						currentItem.setLastError(e.getMessage());
						dao.update(currentItem);
						break;
					}

					Element root = document.getDocumentElement();
					if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("72") == 0) {
						log.debug("Report list returned successfully");

						NodeList objects = root.getElementsByTagName("object");
						String objectId;
						String similarityScore;
						String overlap = "";
						log.debug(objects.getLength() + " objects in the returned list");
						for (int i=0; i<objects.getLength(); i++) {
							similarityScore = ((CharacterData) (((Element)(objects.item(i))).getElementsByTagName("similarityScore").item(0).getFirstChild())).getData().trim();
							objectId = ((CharacterData) (((Element)(objects.item(i))).getElementsByTagName("objectID").item(0).getFirstChild())).getData().trim();
							if (similarityScore.compareTo("-1") != 0) {
								overlap = ((CharacterData) (((Element)(objects.item(i))).getElementsByTagName("overlap").item(0).getFirstChild())).getData().trim();
								reportTable.put(objectId, Integer.valueOf(overlap));
							} else {
								reportTable.put(objectId, -1);
							}

							log.debug("objectId: " + objectId + " similarity: " + similarityScore + " overlap: " + overlap);
						}
					} else {
						log.debug("Report list request not successful");
						log.debug(document.getTextContent());

					}
				}

				int reportVal;
				// check if the report value is now there (there may have been a
				// failure to get the list above)
				if (reportTable.containsKey(currentItem.getExternalId())) {
					reportVal = ((reportTable.get(currentItem.getExternalId())));
					log.debug("reportVal for " + currentItem.getExternalId() + ": " + reportVal);
					if (reportVal != -1) {
						currentItem.setReviewScore(reportVal);
						currentItem.setStatus(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE);
						currentItem.setDateReportReceived(new Date());
						currentItem.setLastError(null);
						currentItem.setErrorCode(null);
						dao.update(currentItem);

						try
						{
							ContentResource resource = contentHostingService.getResource( currentItem.getContentId() );
							boolean itemUpdated = updateItemAccess( resource.getId() );
							if( !itemUpdated )
							{
								log.error( "Could not update cr item access status" );
							}
						}
						catch( PermissionException | IdUnusedException | TypeException ex )
						{
							log.error( "Could not update cr item access status", ex );
						}

						log.debug("new report received: " + currentItem.getExternalId() + " -> " + currentItem.getReviewScore());
					}
				}
			}
			catch (Exception e)
			{
				log.error(e.getMessage() + "\n" + e.getStackTrace());
			}
		}

		log.info("Finished fetching reports from Turnitin");
	}

	// returns null if no valid email exists
	public String getEmail(User user) {

		String uem = null;

		// Check account email address
		String account_email = null;

		if (isValidEmail(user.getEmail())) {
			account_email = user.getEmail().trim();
		}

		// Lookup system profile email address if necessary
		String profile_email = null;
		if (account_email == null || preferSystemProfileEmail) {
			SakaiPerson sp = sakaiPersonManager.getSakaiPerson(user.getId(), sakaiPersonManager.getSystemMutableType());
			if (sp != null && isValidEmail(sp.getMail())) {
				profile_email = sp.getMail().trim();
			}
		}

		// Check guest accounts and use eid as the email if preferred
		if (this.preferGuestEidEmail && isValidEmail(user.getEid())) {
			uem = user.getEid();
		}

		if (uem == null && preferSystemProfileEmail && profile_email != null) {
			uem = profile_email;
		}

		if (uem == null && account_email != null) {
			uem = account_email;
		}

		// Randomize the email address if preferred
		if (spoilEmailAddresses && uem != null) {
			// Scramble it
			String[] parts = uem.split("@");

			String emailName = parts[0];

			Random random = new Random();
			int int1 = random.nextInt();
			int int2 = random.nextInt();
			int int3 = random.nextInt();

			emailName += (int1 + int2 + int3);

			uem = emailName + "@" + parts[1];

			if (log.isDebugEnabled()) {
				log.debug("SCRAMBLED EMAIL:" + uem);
			}
		}

		log.debug("Using email " + uem + " for user eid " + user.getEid() + " id " + user.getId());
		return uem;
	}


	/**
	 * Is this a valid email the service will recognize
	 * @param email
	 * @return
	 */
	private boolean isValidEmail(String email) {

		// TODO: Use a generic Sakai utility class (when a suitable one exists)

		if (email == null || email.equals(""))
		{
			return false;
		}

		email = email.trim();
		//must contain @
		if (!email.contains( "@" ))
		{
			return false;
		}

		//an email can't contain spaces
		if (email.indexOf(" ") > 0)
		{
			return false;
		}

		//use commons-validator
		EmailValidator validator = EmailValidator.getInstance();
		return validator.isValid(email);
	}


	//Methods for updating all assignments that exist
	public void doAssignments() {
		log.info("About to update all turnitin assignments");
		String statement = "Select siteid,taskid from CONTENTREVIEW_ITEM group by siteid,taskid";
		Object[] fields = new Object[0];
		List objects = sqlService.dbRead(statement, fields, new SqlReader(){
			public Object readSqlResultRecord(ResultSet result)
			{
				try {
					ContentReviewItem c = new ContentReviewItem();
					c.setSiteId(result.getString(1));
					c.setTaskId(result.getString(2));
					return c;
				} catch (SQLException e) {
					log.debug( e );
					return null;
				}

			}
		});

		for (int i = 0; i < objects.size(); i ++) {
			ContentReviewItem cri = (ContentReviewItem) objects.get(i);
			try {
				updateAssignment(cri.getSiteId(),cri.getTaskId());
			} catch (SubmissionException e) {
				log.debug( e );
			}

		}
	}

	/**
	 * Update Assignment. This method is not currently called by Assignments 1.
	 * @param siteId
	 * @param taskId
	 * @throws org.sakaiproject.contentreview.exception.SubmissionException
	 */
	public void updateAssignment(String siteId, String taskId) throws SubmissionException {
		log.info("updateAssignment(" + siteId +" , " + taskId + ")");
		//get the assignment reference
		String taskTitle = getAssignmentTitle(taskId);
		log.debug("Creating assignment for site: " + siteId + ", task: " + taskId +" tasktitle: " + taskTitle);

		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat.getDateInstance());
		dform.applyPattern(TURNITIN_DATETIME_FORMAT);
		Calendar cal = Calendar.getInstance();
		//set this to yesterday so we avoid timezpne problems etc
		cal.add(Calendar.DAY_OF_MONTH, -1);
		String dtstart = dform.format(cal.getTime());


		//set the due dates for the assignments to be in 5 month's time
		//turnitin automatically sets each class end date to 6 months after it is created
		//the assignment end date must be on or before the class end date

		//TODO use the 'secret' function to change this to longer
		cal.add(Calendar.MONTH, 5);
		String dtdue = dform.format(cal.getTime());

		String fcmd = "3";						//new assignment
		String fid = "4";						//function id
		String utp = "2"; 					//user type 2 = instructor
		String s_view_report = "1";

                                            //erater
		String erater = "0";
                                           String ets_handbook ="1";
                                           String ets_dictionary="en";
                                           String ets_spelling = "1";
                                           String ets_style = "1";
                                           String ets_grammar = "1";
                                           String ets_mechanics = "1";
                                           String ets_usage = "1";

		String cid = siteId;
		String assignid = taskId;
		String assign = taskTitle;
		String ctl = siteId;

		String assignEnc = assign;
		try {
			if (assign.contains("&")) {
				//log.debug("replacing & in assignment title");
				assign = assign.replace('&', 'n');

			}
			assignEnc = assign;
			log.debug("Assign title is " + assignEnc);

		}
		catch (Exception e) {
			log.debug( e );
		}

		Map params = TurnitinAPIUtil.packMap(turnitinConn.getBaseTIIOptions(),
				"assign", assignEnc,
				"assignid", assignid,
				"cid", cid,
				"ctl", ctl,
				"dtdue", dtdue,
				"dtstart", dtstart,
				"fcmd", fcmd,
				"fid", fid,
				"s_view_report", s_view_report,
				"utp", utp,
                                                                                      "erater",erater,
                                                                                      "ets_handbook",ets_handbook,
                                                                                      "ets_dictionary",ets_dictionary,
                                                                                      "ets_spelling",ets_spelling,
                                                                                      "ets_style",ets_style,
                                                                                      "ets_grammar",ets_grammar,
                                                                                      "ets_mechanics",ets_mechanics,
                                                                                      "ets_usage",ets_usage
		);

		params.putAll(getInstructorInfo(siteId));

		Document document;

		try {
			document = turnitinConn.callTurnitinReturnDocument(params);
		}
		catch (TransientSubmissionException | SubmissionException tse) {
			log.error("Error on API call in updateAssignment siteid: " + siteId + " taskid: " + taskId, tse);
			return;
		}

		Element root = document.getDocumentElement();
		int rcode = new Integer(((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
		if ((rcode > 0 && rcode < 100) || rcode == 419) {
			log.debug("Create Assignment successful");
		} else {
			log.debug("Assignment creation failed with message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
			throw new SubmissionException("Create Assignment not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
		}
	}

	// SAK-27857	--bbailla2
	public boolean allowAllContent()
	{
		// Turntin reports errors when content is submitted that it can't check originality against. So we will block unsupported content.
		return serverConfigurationService.getBoolean(PROP_ACCEPT_ALL_FILES, false);
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.contentreview.service.ContentReviewService#isAcceptableContent(org.sakaiproject.content.api.ContentResource)
	 */
	public boolean isAcceptableContent(ContentResource resource) {
		return turnitinContentValidator.isAcceptableContent(resource);
	}

	// SAK-27857	--bbailla2
	public String[] getAcceptableFileExtensions()
	{
		String[] extensions = serverConfigurationService.getStrings(PROP_ACCEPTABLE_FILE_EXTENSIONS);
		if (extensions != null && extensions.length > 0)
		{
			return extensions;
		}
		return DEFAULT_ACCEPTABLE_FILE_EXTENSIONS;
	}

	// TII-157	--bbailla2
	public String[] getAcceptableMimeTypes()
	{
		String[] mimeTypes = serverConfigurationService.getStrings(PROP_ACCEPTABLE_MIME_TYPES);
		if (mimeTypes != null && mimeTypes.length > 0)
		{
			return mimeTypes;
		}
		return DEFAULT_ACCEPTABLE_MIME_TYPES;
	}

	// TII-157	--bbailla2
	public String [] getAcceptableFileTypes()
	{
		return serverConfigurationService.getStrings(PROP_ACCEPTABLE_FILE_TYPES);
	}

	// TII-157	--bbailla2
	public Map<String, SortedSet<String>> getAcceptableExtensionsToMimeTypes()
	{
		Map<String, SortedSet<String>> acceptableExtensionsToMimeTypes = new HashMap<>();
		String[] acceptableFileExtensions = getAcceptableFileExtensions();
		String[] acceptableMimeTypes = getAcceptableMimeTypes();
		int min = Math.min(acceptableFileExtensions.length, acceptableMimeTypes.length);
		for (int i = 0; i < min; i++)
		{
			appendToMap(acceptableExtensionsToMimeTypes, acceptableFileExtensions[i], acceptableMimeTypes[i]);
		}

		return acceptableExtensionsToMimeTypes;
	}

	// TII-157	--bbailla2
	public Map<String, SortedSet<String>> getAcceptableFileTypesToExtensions()
	{
		Map<String, SortedSet<String>> acceptableFileTypesToExtensions = new LinkedHashMap<>();
		String[] acceptableFileTypes = getAcceptableFileTypes();
		String[] acceptableFileExtensions = getAcceptableFileExtensions();
		if (acceptableFileTypes != null && acceptableFileTypes.length > 0)
		{
			// The acceptable file types are listed in sakai.properties. Sakai.properties takes precedence.
			int min = Math.min(acceptableFileTypes.length, acceptableFileExtensions.length);
			for (int i = 0; i < min; i++)
			{
				appendToMap(acceptableFileTypesToExtensions, acceptableFileTypes[i], acceptableFileExtensions[i]);
			}
		}
		else
		{
			/*
			 * acceptableFileTypes not specified in sakai.properties (this is normal).
			 * Use ResourceLoader to resolve the file types.
			 * If the resource loader doesn't find the file extenions, log a warning and return the [missing key...] messages
			 */
			ResourceLoader resourceLoader = new ResourceLoader("turnitin");
			for( String fileExtension : acceptableFileExtensions )
			{
				String key = KEY_FILE_TYPE_PREFIX + fileExtension;
				if (!resourceLoader.getIsValid(key))
				{
					log.warn("While resolving acceptable file types for Turnitin, the sakai.property " + PROP_ACCEPTABLE_FILE_TYPES + " is not set, and the message bundle " + key + " could not be resolved. Displaying [missing key ...] to the user");
				}
				String fileType = resourceLoader.getString(key);
				appendToMap( acceptableFileTypesToExtensions, fileType, fileExtension );
			}
		}

		return acceptableFileTypesToExtensions;
	}

	// TII-157	--bbailla2
	/**
	 * Inserts (key, value) into a Map<String, Set<String>> such that value is inserted into the value Set associated with key.
	 * The value set is implemented as a TreeSet, so the Strings will be in alphabetical order
	 * Eg. if we insert (a, b) and (a, c) into map, then map.get(a) will return {b, c}
	 */
	private void appendToMap(Map<String, SortedSet<String>> map, String key, String value)
	{
		SortedSet<String> valueList = map.get(key);
		if (valueList == null)
		{
			valueList = new TreeSet<>();
			map.put(key, valueList);
		}
		valueList.add(value);
	}

	public boolean isAcceptableSize(ContentResource resource) {
		return turnitinContentValidator.isAcceptableSize(resource);
	}
	
	/**
	 * find the next time this item should be tried
	 * @param retryCount
	 * @return
	 */
	private Date getNextRetryTime(long retryCount) {
		int offset =5;

		if (retryCount > 9 && retryCount < 20) {

			offset = 10;

		} else if (retryCount > 19 && retryCount < 30) {
			offset = 20;
		} else if (retryCount > 29 && retryCount < 40) {
			offset = 40;
		} else if (retryCount > 39 && retryCount < 50) {
			offset = 80;
		} else if (retryCount > 49 && retryCount < 60) {
			offset = 160;
		} else if (retryCount > 59) {
			offset = 220;
		}

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, offset);
		return cal.getTime();
	}

	/**
	 * Gets a first name for a user or generates an initial from the eid
	 * @param user a sakai user
	 * @return the first name or at least an initial if possible, "X" if no fn can be made
	 */
	public String getUserFirstName(User user) {
		String ufn = user.getFirstName().trim();
		if (ufn == null || ufn.equals("")) {
			boolean genFN = (boolean) serverConfigurationService.getBoolean("turnitin.generate.first.name", true);
			if (genFN) {
				String eid = user.getEid();
				if (eid != null
						&& eid.length() > 0) {
					ufn = eid.substring(0,1);
				} else {
					ufn = "X";
				}
			}
		}
		return ufn;
	}
	
	/**
	 * Get user last Name. If turnitin.generate.last.name is set to true last name is
	 * anonamised
	 * @param user
	 * @return
	 */
	public String getUserLastName(User user){
		String uln = user.getLastName().trim();
		if (uln == null || uln.equals("")) {
			boolean genLN = serverConfigurationService.getBoolean("turnitin.generate.last.name", false);
			if (genLN) {
				String eid = user.getEid();
				if (eid != null 
						&& eid.length() > 0) {
					uln = eid.substring(0,1);        
				} else {
					uln = "X";
				}
			}
		}
		return uln;
	}
	
	public String getLocalizedStatusMessage(String messageCode, String userRef) {

		String userId = EntityReference.getIdFromRef(userRef);
		ResourceLoader resourceLoader = new ResourceLoader(userId, "turnitin");
		return resourceLoader.getString(messageCode);
	}

    public String getReviewError(String contentId) {
    	return getLocalizedReviewErrorMessage(contentId);
    }

	public String getLocalizedStatusMessage(String messageCode) {
		return getLocalizedStatusMessage(messageCode, userDirectoryService.getCurrentUser().getReference());
	}

	public String getLocalizedStatusMessage(String messageCode, Locale locale) {
		//TODO not sure how to do this with  the sakai resource loader
		return null;
	}

	public String getLocalizedReviewErrorMessage(String contentId) {
		log.debug("Returning review error for content: " + contentId);

		List<ContentReviewItem> matchingItems = dao.findByExample(new ContentReviewItem(contentId));

		if (matchingItems.isEmpty()) {
			log.debug("Content " + contentId + " has not been queued previously");
			return null;
		}

		if (matchingItems.size() > 1) {
			log.debug("more than one matching item found - using first item found");
		}

		//its possible the error code column is not populated
		Integer errorCode = ((ContentReviewItem) matchingItems.iterator().next()).getErrorCode();
		if (errorCode == null) {
			return ((ContentReviewItem) matchingItems.iterator().next()).getLastError();
		}
		return getLocalizedStatusMessage(errorCode.toString());
	}

    private String getTEM(String cid) {
        if (turnitinConn.isUseSourceParameter()) {
            return getInstructorInfo(cid).get("uem").toString();
        } else {
            return turnitinConn.getDefaultInstructorEmail();
        }
    }

	/**
	 * This will add to the LTI map the information for the instructor such as
	 * uem, username, ufn, etc. If the system is configured to use src9
	 * provisioning, this will draw information from the current thread based
	 * user. Otherwise it will use the default Instructor information that has
	 * been configured for the system.
	 *
	 * @param ltiProps
	 * @param siteId
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Map putInstructorInfo(Map ltiProps, String siteId) {

		log.debug("Putting instructor info for site " + siteId);

		if (!turnitinConn.isUseSourceParameter()) {
			ltiProps.put("roles", "Instructor");
			ltiProps.put("user_id", turnitinConn.getDefaultInstructorId());
			ltiProps.put("lis_person_contact_email_primary", turnitinConn.getDefaultInstructorEmail());
			ltiProps.put("lis_person_name_given", turnitinConn.getDefaultInstructorFName());
			ltiProps.put("lis_person_name_family", turnitinConn.getDefaultInstructorLName());
			ltiProps.put("lis_person_name_full", turnitinConn.getDefaultInstructorFName() + " " + turnitinConn.getDefaultInstructorLName());
		} else {
			String INST_ROLE = "section.role.instructor";
			User inst = null;
			try {
				Site site = siteService.getSite(siteId);
				User user = userDirectoryService.getCurrentUser();
	
				log.debug("Current user: " + user.getId());

				if (site.isAllowed(user.getId(), INST_ROLE)) {
					inst = user;
				} else {
					Set<String> instIds = getActiveInstructorIds(INST_ROLE,	site);
					if (instIds.size() > 0) {
						inst = userDirectoryService.getUser((String) instIds.toArray()[0]);
					}
				}
			} catch (IdUnusedException e) {
				log.error("Unable to fetch site in putInstructorInfo: " + siteId, e);
			} catch (UserNotDefinedException e) {
				log.error("Unable to fetch user in putInstructorInfo", e);
			}

			if (inst == null) {
				log.error("Instructor is null in putInstructorInfo");
			} else {
				ltiProps.put("roles", "Instructor");
				ltiProps.put("user_id", inst.getId());
				ltiProps.put("lis_person_contact_email_primary", getEmail(inst));
				ltiProps.put("lis_person_name_given", inst.getFirstName());
				ltiProps.put("lis_person_name_family", inst.getLastName());
				ltiProps.put("lis_person_name_full", inst.getDisplayName());
			}
		}

		return ltiProps;
	}
	
	/**
	 * This will return a map of the information for the instructor such as
	 * uem, username, ufn, etc. If the system is configured to use src9
	 * provisioning, this will draw information from the current thread based
	 * user. Otherwise it will use the default Instructor information that has
	 * been configured for the system.
	 *
	 * @param siteId
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Map getInstructorInfo(String siteId) {

		log.debug("Getting instructor info for site " + siteId);

		Map togo = new HashMap();
		if (!turnitinConn.isUseSourceParameter()) {
			togo.put("uem", turnitinConn.getDefaultInstructorEmail());
			togo.put("ufn", turnitinConn.getDefaultInstructorFName());
			togo.put("uln", turnitinConn.getDefaultInstructorLName());
			togo.put("uid", turnitinConn.getDefaultInstructorId());
		}
		else {
			String INST_ROLE = "section.role.instructor";
			User inst = null;
			try {
				Site site = siteService.getSite(siteId);
				User user = userDirectoryService.getCurrentUser();
	
				log.debug("Current user: " + user.getId());

				if (site.isAllowed(user.getId(), INST_ROLE)) {
					inst = user;
				}
				else {
					Set<String> instIds = getActiveInstructorIds(INST_ROLE,
							site);
					if (instIds.size() > 0) {
						inst = userDirectoryService.getUser((String) instIds.toArray()[0]);
					}
				}
			} catch (IdUnusedException e) {
				log.error("Unable to fetch site in getAbsoluteInstructorInfo: " + siteId, e);
			} catch (UserNotDefinedException e) {
				log.error("Unable to fetch user in getAbsoluteInstructorInfo", e);
			}


			if (inst == null) {
				log.error("Instructor is null in getAbsoluteInstructorInfo");
			}
			else {
				togo.put("uem", getEmail(inst));
				togo.put("ufn", inst.getFirstName());
				togo.put("uln", inst.getLastName());
				togo.put("uid", inst.getId());
				togo.put("username", inst.getDisplayName());
			}
		}

		return togo;
	}

	@SuppressWarnings("unchecked")
	public Map getInstructorInfo(String siteId, boolean ignoreUseSource) {
		Map togo = new HashMap();
		if (!turnitinConn.isUseSourceParameter() && ignoreUseSource == false ) {
			togo.put("uem", turnitinConn.getDefaultInstructorEmail());
			togo.put("ufn", turnitinConn.getDefaultInstructorFName());
			togo.put("uln", turnitinConn.getDefaultInstructorLName());
			togo.put("uid", turnitinConn.getDefaultInstructorId());
		}
		else {
			String INST_ROLE = "section.role.instructor";
			User inst = null;
			try {
				Site site = siteService.getSite(siteId);
				User user = userDirectoryService.getCurrentUser();
				if (site.isAllowed(user.getId(), INST_ROLE)) {
					inst = user;
				}
				else {
					Set<String> instIds = getActiveInstructorIds(INST_ROLE,
							site);
					if (instIds.size() > 0) {
						inst = userDirectoryService.getUser((String) instIds.toArray()[0]);
					}
				}
			} catch (IdUnusedException e) {
				log.error("Unable to fetch site in getAbsoluteInstructorInfo: " + siteId, e);
			} catch (UserNotDefinedException e) {
				log.error("Unable to fetch user in getAbsoluteInstructorInfo", e);
			}


			if (inst == null) {
				log.error("Instructor is null in getAbsoluteInstructorInfo");
			}
			else {
				togo.put("uem", getEmail(inst));
				togo.put("ufn", inst.getFirstName());
				togo.put("uln", inst.getLastName());
				togo.put("uid", inst.getId());
				togo.put("username", inst.getDisplayName());
			}
		}

		return togo;
	}

	private Set<String> getActiveInstructorIds(String INST_ROLE, Site site) {

		log.debug("Getting active instructor IDs for permission " + INST_ROLE + " in site " + site.getId());

		Set<String> instIds = site.getUsersIsAllowed(INST_ROLE);

		//the site could contain references to deleted users
		List<User> activeUsers = userDirectoryService.getUsers(instIds);
		Set<String> ret =  new HashSet<>();
		for (int i = 0; i < activeUsers.size(); i++) {
			User user = activeUsers.get(i);
			// Ignore users who do not have a first and/or last name set or do not have
			// a valid email address, as this will cause a TII API call to fail
			if (user.getFirstName() != null && !user.getFirstName().trim().isEmpty() && 
		   	    user.getLastName() != null && !user.getLastName().trim().isEmpty() &&
			    getEmail(user) != null) {
				ret.add(user.getId());
			}
		}

		return ret;
	}

	public String getLegacyReviewReportStudent(String contentId) throws QueueException, ReportException{
		return getReviewReportStudent(contentId);
	}
	
	public String getLegacyReviewReportInstructor(String contentId) throws QueueException, ReportException{
		return getReviewReportStudent(contentId);
	}
	
	public String getLTIAccess(String taskId, String contextId){
		String ltiUrl = null;
		try{
			String ltiId = getActivityConfigValue(TurnitinConstants.STEALTHED_LTI_ID, asnRefToId(taskId), TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID,
					TurnitinConstants.PROVIDER_ID);
			ltiUrl = "/access/basiclti/site/" + contextId + "/content:" + ltiId;
			log.debug("getLTIAccess: " + ltiUrl);
		} catch(Exception e) {
			log.error( "Unexpected exception getting LTI access", e );
		}
		return ltiUrl;
	}

	public String getLTIReportAccess(ContentReviewItem item)
	{
		String ltiReportsUrl = null;
		String contentId = item.getContentId();
		String assignmentId = item.getTaskId();
		String siteId = item.getSiteId();
		try
		{
			String ltiReportsId = siteService.getSite(siteId).getProperties().getProperty("turnitin_reports_lti_id");
			String ltiResourceId = item.getExternalId();
			if (ltiResourceId == null)
			{
				// Fallback: link to assignment
				return getLTIAccess(assignmentId, siteId);
			}
			ltiReportsUrl = "/access/basiclti/site/" + siteId + "/content:" + ltiReportsId + ",resource:" + ltiResourceId;
			log.debug("getLTIRepotAccess: " + ltiReportsUrl);
		}
		catch (Exception e)
		{
			log.warn("Exception while trying to get LTI Reports access for assignment "  + assignmentId + ", resource " + contentId + ", and site " + siteId + ": " + e.getMessage());
		}
		return ltiReportsUrl;
	}
	
	public boolean deleteLTITool(String taskId, String contextId){
		SecurityAdvisor advisor = new SimpleSecurityAdvisor(sessionManager.getCurrentSessionUserId(), "site.upd", "/site/!admin");
		try{
			String ltiId = getActivityConfigValue(TurnitinConstants.STEALTHED_LTI_ID, asnRefToId(taskId), TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID,
					TurnitinConstants.PROVIDER_ID);
			securityService.pushAdvisor(advisor);
			return tiiUtil.deleteTIIToolContent(ltiId);
		} catch(Exception e) {
			log.error( "Unexpected exception deleting TII tool", e );
		} finally {
			securityService.popAdvisor(advisor);
		}
		return false;
	}
	
	/**
	 * Migrates the original LTI XML settings from the assignments table into the new activity config table.
	 * Also moves the external value from the assignment submission/content resource binary entity back into the contentreviewitem table.
	 * You need to run this ONLY if you have previously deployed the LTI integration prior to the introduction of TII-219 and TII-221.
	 */
	@Override
	public void migrateLtiXml()
	{
		// 1. find all the assignments that have the "turnitin_id" and/or "lti_id" values in their content XML
		// For each assignment, insert a row for the turnitin/lti id values
		// Use LTI service to find all the assignments that have Turnitin LTI instances
		Set<String> tiiSites = tiiUtil.getSitesUsingLTI();
		for (String siteId : tiiSites)
		{
			Iterator iter = assignmentService.getAssignmentsForContext(siteId);
			while(iter.hasNext())
			{
				org.sakaiproject.assignment.api.Assignment asn = (org.sakaiproject.assignment.api.Assignment) iter.next();
				AssignmentContent asnContent = asn.getContent();
				if (asnContent == null)
				{
					log.error("No content for assignment: " + asn.getId());
					continue;
				}
				ResourceProperties asnProps = asnContent.getProperties();
				if (asnProps == null)
				{
					log.error("No properties for assignment: " + asn.getId());
					continue;
				}
				String turnitinId = (String) asnProps.get("turnitin_id");
				String ltiId = (String) asnProps.get("lti_id");
				if (StringUtils.isNotBlank(turnitinId))
				{
					// update cfg table
					log.info(String.format("Add tii id %s for asn %s", turnitinId, asn.getId()));
					boolean success = saveOrUpdateActivityConfigEntry(TurnitinConstants.TURNITIN_ASN_ID, turnitinId, asn.getId(),
					TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID, TurnitinConstants.PROVIDER_ID, false);
					if (!success)
					{
						log.error(String.format("Unable to migrate turnitinId %s for assignment %s to the activity_cfg table. An entry for this assignment may already exist.", turnitinId, asn.getId()));
					}
				}
				if (StringUtils.isNotBlank(ltiId))
				{
					//update cfg table
					log.info(String.format("Add lti id %s for asn %s", ltiId, asn.getId()));
					boolean success = saveOrUpdateActivityConfigEntry(TurnitinConstants.STEALTHED_LTI_ID, ltiId, asn.getId(),
					TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID, TurnitinConstants.PROVIDER_ID, false);
					if (!success)
					{
						log.error(String.format("Unable to migrate ltiId %s for assignment %s to the activity_cfg table. An entry for this assignment may already exist.", ltiId, asn.getId()));
					}
				}
				
				// 2. for each contentreviewitem related to this assignment with a null external id, retrieve the 
				// assignment submission and perhaps the binary entity for the resource item.
				// If the submission/entity has a turnitin_id value, insert it into the contentreviewitem table
				Search search = new Search();
				search.addRestriction(new Restriction("siteId", siteId));
				search.addRestriction(new Restriction("taskId", asn.getReference()));
				List<ContentReviewItem> ltiContentItems = dao.findBySearch(ContentReviewItem.class, search);
				for (ContentReviewItem item : ltiContentItems)
				{
					if (StringUtils.isNotBlank(item.getExternalId()))
					{
						continue;
					}
					
					try
					{
						String tiiPaperId;
						AssignmentSubmission as = assignmentService.getSubmission(item.getSubmissionId());						
						ResourceProperties aProperties = as.getProperties();
						tiiPaperId = aProperties.getProperty("turnitin_id");
						if (StringUtils.isBlank(tiiPaperId)) // not found in submission, check content item
						{
							ContentResource content = contentHostingService.getResource(item.getContentId());
							ResourceProperties cProperties = content.getProperties();
							tiiPaperId = cProperties.getProperty("turnitin_id");
						}
						
						if (StringUtils.isNotBlank(tiiPaperId))
						{
							log.info("Will write " + tiiPaperId + " as external id for item " + item.getId());
							dao.updateExternalId(item.getContentId(), tiiPaperId);
						}
					}
					catch (Exception e)
					{
						log.error("Exception attempting to read/write paperId/externalId", e);
					}
				}
				
			}
			
		}
	}

	@Override
	public boolean validateActivityConfiguration(String toolId, String activityId)
	{
		// if new integration, check for the turnitin assignment id and the stealthed lti id
		boolean useLTI;
		try
		{
			// assume we're always in assignments since this is just a temporary check until
			// we remove the legacy integration
			org.sakaiproject.assignment.api.Assignment asn = assignmentService.getAssignment(activityId);
			Site site = siteService.getSite(asn.getContext());
			useLTI = siteAdvisor.siteCanUseLTIReviewServiceForAssignment(site, new Date(asn.getTimeCreated().getTime()));
		}
		catch (IdUnusedException | PermissionException e)
		{
			log.debug("Unable to find Assignment for the given activity id (" + activityId + ")", e);
			return false;
		}

		return !useLTI || (!getActivityConfigValue(TurnitinConstants.TURNITIN_ASN_ID, activityId, toolId, TurnitinConstants.PROVIDER_ID).isEmpty()
				&& !getActivityConfigValue(TurnitinConstants.STEALTHED_LTI_ID, activityId, toolId, TurnitinConstants.PROVIDER_ID).isEmpty());
	}

	@Override
	public String getLocalizedInvalidAsnConfigError()
	{
		ResourceLoader rl = new ResourceLoader(userDirectoryService.getCurrentUser().getId(), "turnitin");
		
		return rl.getString("invalid_asn_config");
	}

	private List<ContentResource> getAllAcceptableAttachments(AssignmentSubmission sub, boolean allowAnyFile){
		List attachments = sub.getSubmittedAttachments();
		List<ContentResource> resources = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i++) {
            Reference attachment = (Reference) attachments.get(i);
            try {
                ContentResource res = contentHostingService.getResource(attachment.getId());
                if (isAcceptableSize(res) && (allowAnyFile || isAcceptableContent(res))) {
                    resources.add(res);
                }
            } catch (PermissionException | IdUnusedException | TypeException e) {
                log.warn(":getAllAcceptableAttachments " + e.getMessage());
            }
        }
        return resources;
	}

	private void processError( ContentReviewItem item, Long status, String error, Integer errorCode )
	{
		try
		{
			if( status == null )
			{
				IllegalArgumentException ex = new IllegalArgumentException( "Status is null; you must supply a valid status to update when calling processError()" );
				throw ex;
			}
			else
			{
				item.setStatus( status );
			}
			if( error != null )
			{
				item.setLastError(error);
			}
			if( errorCode != null )
			{
				item.setErrorCode( errorCode );
			}

			dao.update( item );

			// Update urlAccessed to true if status is being updated to one of the dead states (5, 6, 8 and 9)
			if( isDeadState( status ) )
			{
				try
				{
					ContentResource resource = contentHostingService.getResource( item.getContentId() );
					boolean itemUpdated = updateItemAccess( resource.getId() );
					if (!itemUpdated)
					{
						log.error( "Could not update cr item access status" );
					}
				}
				catch( PermissionException | IdUnusedException | TypeException ex )
				{
					log.error( "Error updating cr item access status; item id = " + item.getContentId(), ex );
				}
			}
		}
		finally
		{
			releaseLock( item );
		}
	}

	/**
	 * Returns true/false if the given status is one of the 'dead' TII states
	 * @param status the status to check
	 * @return true if the status given is of one of the dead states; false otherwise
	 */
	private boolean isDeadState( Long status )
	{
		return status != null && (status.equals( ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE ) || status.equals( ContentReviewItem.REPORT_ERROR_NO_RETRY_CODE ) 
			|| status.equals( ContentReviewItem.SUBMISSION_ERROR_RETRY_EXCEEDED ));
	}
	
	private Optional<Date> getAssignmentCreationDate(String assignmentRef)
	{
		try
		{
			org.sakaiproject.assignment.api.Assignment asn = assignmentService.getAssignment(assignmentRef);
			return getAssignmentCreationDate(asn);
		}
		catch(IdUnusedException | PermissionException e)
		{
			return Optional.empty();
		}
	}

	private Optional<Date> getAssignmentCreationDate(org.sakaiproject.assignment.api.Assignment asn)
	{
		if (asn == null)
		{
			return Optional.empty();
		}
		Date date = new Date(asn.getTimeCreated().getTime());
		return Optional.of(date);
	}

	/**
	 * A simple SecurityAdviser that can be used to override permissions for one user for one function.
	 */
	protected class SimpleSecurityAdvisor implements SecurityAdvisor
	{
		protected String m_userId;
		protected String m_function;
		protected String m_reference;

		public SimpleSecurityAdvisor(String userId, String function, String reference)
		{
			m_userId = userId;
			m_function = function;
			m_reference = reference;
		}

		public SecurityAdvice isAllowed(String userId, String function, String reference)
		{
			SecurityAdvice rv = SecurityAdvice.PASS;
			if (m_userId.equals(userId) && m_function.equals(function) && m_reference.equals(reference))
			{
				rv = SecurityAdvice.ALLOWED;
			}
			return rv;
		}
	}
	
	// copied from protected method assignmentId() in BaseAssignmentService
	// might be better to just make that method public
	private String asnRefToId(String ref)
	{
		if (ref == null) return ref;
		int i = ref.lastIndexOf(Entity.SEPARATOR);
		if (i == -1) return ref;
		String id = ref.substring(i + 1);
		return id;
	}

}
