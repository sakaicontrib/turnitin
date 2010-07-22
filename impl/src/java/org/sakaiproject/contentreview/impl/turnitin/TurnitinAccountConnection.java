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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.turnitin.util.TurnitinAPIUtil;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.w3c.dom.Document;

/**
 * This class contains the properties and utility methods so it can be used to
 * make API calls and connections to a specific Turnitin Account.
 * 
 * Ideally you can make several of these in a single Sakai System in the event
 * that you need to use different different Turnitin Accounts for different
 * tools or provisioned user spaces (such as different campuses, etc).
 * 
 * A large portion of this was factored out of TurnitinReviewService where it
 * originally occurred.
 * 
 * @author sgithens
 *
 */
public class TurnitinAccountConnection {
	private static final Log log = LogFactory.getLog(TurnitinAccountConnection.class);

	private String aid = null;
	private String said = null;
	private String secretKey = null;
	private String apiURL = "https://www.turnitin.com/api.asp?";
	private String proxyHost = null;
	private String proxyPort = null;
	final static long LOCK_PERIOD = 12000000;
	private String defaultInstructorEmail = null;
	private String defaultInstructorFName = null;
	private String defaultInstructorLName = null;
	private String defaultInstructorPassword = null;
	private boolean useSourceParameter = false;
	private int turnitinConnTimeout = 0; // Default to 0, no timeout.
	private int sendAccountNotifications = 0;
	private int sendSubmissionNotification = 0;
	private Long maxRetry = null;

	// Proxy if set
	private Proxy proxy = null; 

	//note that the assignment id actually has to be unique globally so use this as a prefix
	// eg. assignid = defaultAssignId + siteId
	private String defaultAssignId = null;

	private String defaultClassPassword = null;

	//private static final String defaultInstructorId = defaultInstructorFName + " " + defaultInstructorLName;
	private String defaultInstructorId = null;

	public void init() {

		log.info("init()");

		proxyHost = serverConfigurationService.getString("turnitin.proxyHost"); 

		proxyPort = serverConfigurationService.getString("turnitin.proxyPort");



		if (!"".equals(proxyHost) && !"".equals(proxyPort)) {
			try {
				SocketAddress addr = new InetSocketAddress(proxyHost, new Integer(proxyPort).intValue());
				proxy = new Proxy(Proxy.Type.HTTP, addr);
				log.debug("Using proxy: " + proxyHost + " " + proxyPort);
			} catch (NumberFormatException e) {
				log.debug("Invalid proxy port specified: " + proxyPort);
			}
		} else if (System.getProperty("http.proxyHost") != null && !System.getProperty("http.proxyHost").equals("")) {
			try {
				SocketAddress addr = new InetSocketAddress(System.getProperty("http.proxyHost"), new Integer(System.getProperty("http.proxyPort")).intValue());
				proxy = new Proxy(Proxy.Type.HTTP, addr);
				log.debug("Using proxy: " + System.getProperty("http.proxyHost") + " " + System.getProperty("http.proxyPort"));
			} catch (NumberFormatException e) {
				log.debug("Invalid proxy port specified: " + System.getProperty("http.proxyPort"));
			}
		}

		aid = serverConfigurationService.getString("turnitin.aid");

		said = serverConfigurationService.getString("turnitin.said");

		secretKey = serverConfigurationService.getString("turnitin.secretKey");

		apiURL = serverConfigurationService.getString("turnitin.apiURL","https://www.turnitin.com/api.asp?");



		defaultInstructorEmail = serverConfigurationService.getString("turnitin.defaultInstructorEmail");

		defaultInstructorFName = serverConfigurationService.getString("turnitin.defaultInstructorFName");

		defaultInstructorLName = serverConfigurationService.getString("turnitin.defaultInstructorLName");

		defaultInstructorPassword = serverConfigurationService.getString("turnitin.defaultInstructorPassword");

		useSourceParameter = serverConfigurationService.getBoolean("turnitin.useSourceParameter", false);

		/*
		 * Previously, we only had the sendnotifications option. We're keeping it here,
		 * and running it first for backwards compatibility. Because of functional
		 * requirements we need more control over whether emails are sent for specific 
		 * operations, thus the new options.
		 */
		if (!serverConfigurationService.getBoolean("turnitin.sendnotifications", true)) {
			sendAccountNotifications = 1;
			sendSubmissionNotification = 1;
		} 
		else {
			sendAccountNotifications = 0;
			sendSubmissionNotification = 0;
		}

		if  (!serverConfigurationService.getBoolean("turnitin.sendAccountNotifications", true)) {
			sendAccountNotifications = 1;
		}
		else {
			sendAccountNotifications = 0;
		}

		if  (!serverConfigurationService.getBoolean("turnitin.sendSubmissionNotifications", true)) {
			sendSubmissionNotification = 1;
		}
		else {
			sendSubmissionNotification = 0;
		}


		//note that the assignment id actually has to be unique globally so use this as a prefix
		// assignid = defaultAssignId + siteId
		defaultAssignId = serverConfigurationService.getString("turnitin.defaultAssignId");;

		defaultClassPassword = serverConfigurationService.getString("turnitin.defaultClassPassword","changeit");;

		//private static final String defaultInstructorId = defaultInstructorFName + " " + defaultInstructorLName;
		defaultInstructorId = serverConfigurationService.getString("turnitin.defaultInstructorId","admin");

		maxRetry = Long.valueOf(serverConfigurationService.getInt("turnitin.maxRetry",100));

		/* TODO This still needs to happen in the TurnitinReviewServiceImpl
		if (!useSourceParameter) {
			if (serverConfigurationService.getBoolean("turnitin.updateAssingments", false))
				doAssignments();
		}
		 */

		turnitinConnTimeout = serverConfigurationService.getInt("turnitin.networkTimeout", 0);

	}

	/*
	 * Utility Methods below
	 */

	/**
	 * Get's a Map of TII options that are the same for every one of these
	 * calls. Things like encrpyt and diagnostic.
	 * 
	 * This can be used as well for changing things dynamically and testing.
	 * 
	 * @return
	 */
	public Map getBaseTIIOptions() {
		String diagnostic = "0"; //0 = off; 1 = on
		String encrypt = "0"; //encryption flag

		Map togo = TurnitinAPIUtil.packMap(null, 
				"diagnostic", diagnostic,
				"encrypt", encrypt,
				"said", said,
				"aid", aid
		);

		if (useSourceParameter) {
			togo.put("src", "9");
		}

		return togo;
	}

	/**
	 * This will return a map of the information for the instructor such as 
	 * uem, username, ufn, etc. If the system is configured to use src9 
	 * provisioning, this will draw information from the current thread based
	 * user. Otherwise it will use the default Instructor information that has
	 * been configured for the system.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Map getInstructorInfo(String siteId) {
		Map togo = new HashMap();
		if (!useSourceParameter) {
			togo.put("uem", defaultInstructorEmail);
			togo.put("ufn", defaultInstructorFName);
			togo.put("uln", defaultInstructorLName);
			togo.put("uid", defaultInstructorId);
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
					Set<String> instIds = site.getUsersIsAllowed(INST_ROLE);
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
				togo.put("uem", inst.getEmail());
				togo.put("ufn", inst.getFirstName());
				togo.put("uln", inst.getLastName());
				togo.put("uid", inst.getId());
				togo.put("username", inst.getDisplayName());
			}
		}

		return togo;
	}

	public String getTEM(String cid) {
		if (useSourceParameter) {
			//return cid + "_" + this.aid + "@tiisakai.com";
			return getInstructorInfo(cid).get("uem").toString();
		} else {
			return defaultInstructorEmail;
		}
	}

	public Map callTurnitinReturnMap(Map params) throws TransientSubmissionException, SubmissionException {
		return TurnitinAPIUtil.callTurnitinReturnMap(apiURL, params, secretKey, turnitinConnTimeout, proxy);
	}

	public Document callTurnitinReturnDocument(Map params) throws TransientSubmissionException, SubmissionException {
		return TurnitinAPIUtil.callTurnitinReturnDocument(apiURL, params, secretKey, turnitinConnTimeout, proxy, false);
	}

	public Document callTurnitinReturnDocument(Map params, boolean multiPart) throws TransientSubmissionException, SubmissionException {
		return TurnitinAPIUtil.callTurnitinReturnDocument(apiURL, params, secretKey, turnitinConnTimeout, proxy, multiPart);
	}

	public Map callTurnitinWDefaultsReturnMap(Map params) throws SubmissionException, TransientSubmissionException {
		params.putAll(getBaseTIIOptions());
		return TurnitinAPIUtil.callTurnitinReturnMap(apiURL, params, secretKey, turnitinConnTimeout, proxy);
	}

	public InputStream callTurnitinWDefaultsReturnInputStream(Map params) throws SubmissionException, TransientSubmissionException {
		params.putAll(getBaseTIIOptions());
		return TurnitinAPIUtil.callTurnitinReturnInputStream(apiURL, params, secretKey, turnitinConnTimeout, proxy, false);
	}

	public Document callTurnitinWDefaultsReturnDocument(Map params) throws SubmissionException, TransientSubmissionException {
		params.putAll(getBaseTIIOptions());
		return TurnitinAPIUtil.callTurnitinReturnDocument(apiURL, params, secretKey, turnitinConnTimeout, proxy, false);
	}

	public String buildTurnitinURL(Map params) {
		return TurnitinAPIUtil.buildTurnitinURL(apiURL, params, secretKey);
	}


	/*
	 * Dependency Getters/Setters Below
	 */
	public boolean isUseSourceParameter() {
		return useSourceParameter;
	}

	public void setUseSourceParameter(boolean useSourceParameter) {
		this.useSourceParameter = useSourceParameter;
	}

	// Dependency
	private ServerConfigurationService serverConfigurationService; 
	public void setServerConfigurationService (ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	private SiteService siteService;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

	public int getSendAccountNotifications() {
		return sendAccountNotifications;
	}

	public void setSendAccountNotifications(int sendAccountNotifications) {
		this.sendAccountNotifications = sendAccountNotifications;
	}

	public int getSendSubmissionNotification() {
		return sendSubmissionNotification;
	}

	public void setSendSubmissionNotification(int sendSubmissionNotification) {
		this.sendSubmissionNotification = sendSubmissionNotification;
	}

	public Long getMaxRetry() {
		return maxRetry;
	}

	public void setMaxRetry(Long maxRetry) {
		this.maxRetry = maxRetry;
	}

	public String getDefaultAssignId() {
		return defaultAssignId;
	}

	public void setDefaultAssignId(String defaultAssignId) {
		this.defaultAssignId = defaultAssignId;
	}

	public String getDefaultClassPassword() {
		return defaultClassPassword;
	}

	public void setDefaultClassPassword(String defaultClassPassword) {
		this.defaultClassPassword = defaultClassPassword;
	}
}
