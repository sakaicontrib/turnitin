/**********************************************************************************
 * $URL: 
 * $Id: 
 ***********************************************************************************
 *
 * Copyright (c) 2007 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.contentreview.impl.turnitin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.parsers.DOMParser;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.contentreview.dao.ContentReviewDao;
import org.sakaiproject.contentreview.exception.QueueException;
import org.sakaiproject.contentreview.exception.ReportException;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.assignment.api.Assignment;
import org.sakaiproject.site.api.Site;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class TurnitinReviewServiceImpl implements ContentReviewService {


	private static final String SERVICE_NAME="Turnitin";
	
	private String aid = null;

	private String said = null;

	private String secretKey = null;

	private String apiURL = "https://www.turnitin.com/api.asp?";

	private String defaultAssignmentName = null;

	private String defaultInstructorEmail = null;
	
	private String defaultInstructorFName = null;
	
	private String defaultInstructorLName = null;
	
	private String defaultInstructorPassword = null;

	//note that the assignment id actually has to be unique globally so use this as a prefix
	// eg. assignid = defaultAssignId + siteId
	private String defaultAssignId = null;
	
	private String defaultClassPassword = null;
	
	//private static final String defaultInstructorId = defaultInstructorFName + " " + defaultInstructorLName;
	private String defaultInstructorId = null;

	private static final Log log = LogFactory
			.getLog(TurnitinReviewServiceImpl.class);

	private ContentReviewDao dao;

	public void setDao(ContentReviewDao dao) {
		this.dao = dao;
	}

	private ToolManager toolManager;

	public void setToolManager(ToolManager toolManager) {
		this.toolManager = toolManager;
	}

	private UserDirectoryService userDirectoryService;

	public void setUserDirectoryService(
			UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}
	
	private EntityManager entityManager;
	public void setEntityManager(EntityManager en){
		this.entityManager = en;
	}

	private ContentHostingService contentHostingService;

	public void setContentHostingService(
			ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}

	
	private ServerConfigurationService serverConfigurationService; public
	void setServerConfigurationService (ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}
	

	/**
	 * Place any code that should run when this class is initialized by spring
	 * here
	 */

	public void init() {
		// TODO check on this function manager - sakai permissions?
		// TODO what does the example init method do exactly? is it important?
		log.debug("init");
		//System.setProperty("javax.net.ssl.trustStore", "dave_keystore");
		//System.setProperty("javax.net.ssl.trustStorePassword", "dave_keystore");
		
		aid = serverConfigurationService.getString("turnitin.aid");

		said = serverConfigurationService.getString("turnitin.said");

		secretKey = serverConfigurationService.getString("turnitin.secretKey");

		apiURL = serverConfigurationService.getString("turnitin.apiURL","https://www.turnitin.com/api.asp?");

		defaultAssignmentName = serverConfigurationService.getString("turnitin.defaultAssignmentName");

		defaultInstructorEmail = serverConfigurationService.getString("turnitin.defaultInstructorEmail");
		
		defaultInstructorFName = serverConfigurationService.getString("turnitin.defaultInstructorFName");;
		
		defaultInstructorLName = serverConfigurationService.getString("turnitin.defaultInstructorLName");;
		
		defaultInstructorPassword = serverConfigurationService.getString("turnitin.defaultInstructorPassword");;

		//note that the assignment id actually has to be unique globally so use this as a prefix
		// assignid = defaultAssignId + siteId
		defaultAssignId = serverConfigurationService.getString("turnitin.defaultAssignId");;
		
		defaultClassPassword = serverConfigurationService.getString("turnitin.defaultClassPassword");;
		
		//private static final String defaultInstructorId = defaultInstructorFName + " " + defaultInstructorLName;
		defaultInstructorId = serverConfigurationService.getString("turnitin.defaultInstructorId");;
		
		//get the settings from sakai.properties
		System.setProperty("javax.net.ssl.trustStore", serverConfigurationService.getString("turnitin.keystore_name"));
		System.setProperty("javax.net.ssl.trustStorePassword", serverConfigurationService.getString("turnitin.keystore_password"));
	}

	public void queueContent(String userId, String siteId, String taskId, String contentId)
			throws QueueException {
		log.debug("Method called queueContent(" + userId + "," + siteId + "," + contentId + ")");

		if (userId == null) {
			log.debug("Using current user");
			userId = userDirectoryService.getCurrentUser().getId();
		}

		if (siteId == null) {
			log.debug("Using current site");
			siteId = toolManager.getCurrentPlacement().getContext();
		}
		
		if (taskId == null) {
			log.debug("Generating default taskId");
			taskId = siteId + " " + defaultAssignmentName;
		}

		log.debug("Adding content: " + contentId + " from site " + siteId
				+ " and user: " + userId + " for task: " + taskId + " to submission queue");

		/*
		 * first check that this content has not been submitted before this may
		 * not be the best way to do this - perhaps use contentId as the primary
		 * key for now id is the primary key and so the database won't complain
		 * if we put in repeats necessitating the check
		 */

		List existingItems = dao
				.findByExample(new ContentReviewItem(contentId));
		if (existingItems.size() > 0) {
			log.debug("Content: " + contentId + " is already queued, not re-queued");
			throw new QueueException("Content " + contentId + " is already queued, not re-queued");
		}

		dao.save(new ContentReviewItem(userId, siteId, taskId, contentId, new Date(),
				ContentReviewItem.NOT_SUBMITTED_CODE));
	}
	
	
	

	public int getReviewScore(String contentId)
			throws QueueException, ReportException, Exception {
		log.debug("Getting review score for content: " + contentId);

		List matchingItems = dao.findByExample(new ContentReviewItem(contentId));
		if (matchingItems.size() == 0) {
			log.debug("Content " + contentId + " has not been queued previously");
			throw new QueueException("Content " + contentId + " has not been queued previously");
		}

		if (matchingItems.size() > 1)
			log.debug("More than one matching item - using first item found");

		ContentReviewItem item = (ContentReviewItem) matchingItems.iterator().next();
		if (item.getStatus().compareTo(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE) != 0) {
			log.debug("Report not available: " + item.getStatus());
			throw new ReportException("Report not available: " + item.getStatus());
		}
		
		return item.getReviewScore().intValue();
	}
	
	
	

	public String getReviewReport(String contentId)
		throws QueueException, ReportException {

		// first retreive the record from the database to get the externalId of
		// the content
		log.debug("Getting report for content: " + contentId);

		List matchingItems = dao.findByExample(new ContentReviewItem(contentId));
		if (matchingItems.size() == 0) {
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
		String encrypt = "0";
		String diagnostic = "0";
		String uem = defaultInstructorEmail;
		String ufn = defaultInstructorFName;
		String uln = defaultInstructorLName;
		String utp = "2";

		// is it worthwhile using this?
		String uid = defaultInstructorId;
		String cid = item.getSiteId();
		String assignid = defaultAssignId + item.getSiteId();
		
		/*User user = userDirectoryService.getUser(item.getUserId());
		String uem = user.getEmail();
		String ufn = user.getFirstName();
		String uln = user.getLastName();
		String utp = "1";

		// is it worthwhile using this?
		String uid = item.getUserId();
		String cid = item.getSiteId();*/
		
		String gmtime = getGMTime();

		// note that these vars must be ordered alphabetically according to
		// their names with secretKey last
		String md5_str = aid + assignid + cid + diagnostic + encrypt + fcmd + fid + gmtime + oid
				+ said + uem + ufn + uid + uln + utp + secretKey;

		String md5;
		try {
			md5 = getMD5(md5_str);
		} catch (Throwable t) {
			throw new ReportException("Cannont do MD5 hash of data for Turnitin API call to retrieve report", t);
		}

		String reportURL = apiURL;

		reportURL += "fid=";
		reportURL += fid;

		reportURL += "&fcmd=";
		reportURL += fcmd;
		
		reportURL += "&assignid=";
		reportURL += assignid;
		
		reportURL += "&uid=";
		reportURL += uid;

		reportURL += "&cid=";
		reportURL += cid;
		
		reportURL += "&encrypt=";
		reportURL += encrypt;

		reportURL += "&aid=";
		reportURL += aid;

		reportURL += "&said=";
		reportURL += said;

		reportURL += "&diagnostic=";
		reportURL += diagnostic;

		reportURL += "&oid=";
		reportURL += oid;

		reportURL += "&uem=";
		reportURL += uem;

		reportURL += "&ufn=";
		reportURL += ufn;

		reportURL += "&uln=";
		reportURL += uln;

		reportURL += "&utp=";
		reportURL += utp;

		reportURL += "&gmtime=";
		reportURL += gmtime;

		reportURL += "&md5=";
		reportURL += md5;

		return reportURL;
	}

	// TODO put this somewhere else
	// ///////////////////////////////////IGNORE THIS
	// STUFF/////////////////////////////////////////////
	// this stuff was used to set up the keystore and test https connection
	/*
	 * 
	 * String retStr = "";
	 * 
	 * try{
	 * 
	 * //TODO get this info from somewhere else - the sakai.properties or
	 * something System.setProperty("javax.net.ssl.trustStore",
	 * "dave_keystore"); System.setProperty("javax.net.ssl.trustStorePassword",
	 * "dave_keystore");
	 * 
	 * URL url = new URL("https://www.turnitin.com/api.asp"); HttpsURLConnection
	 * con = (HttpsURLConnection) url.openConnection(); con.setDoOutput(true);
	 * con.getOutputStream().close();
	 * 
	 * BufferedReader in = new BufferedReader(new
	 * InputStreamReader(con.getInputStream()));
	 * 
	 * String inLine = ""; while ((inLine = in.readLine()) != null) { retStr =
	 * retStr.concat(inLine); log.info(inLine); } } catch (SSLHandshakeException
	 * e1) { log.error(e1.toString()); //this stuff only for saving the
	 * necessary certificates to use turnitin CertPath certPath =
	 * ((CertPathValidatorException)(e1.getCause().getCause())).getCertPath();
	 * List certs = certPath.getCertificates(); Iterator it = certs.iterator();
	 * try { KeyStore ks = KeyStore.getInstance("JKS"); //ks.load(new
	 * FileInputStream(new File("dave_keystore")),
	 * "dave_keystore".toCharArray()); ks.load(null,
	 * "dave_keystore".toCharArray()); int i=0; Certificate cert; while
	 * (it.hasNext()) { cert = (Certificate) it.next();
	 * ks.setCertificateEntry("Turnitin path " + i++, cert);
	 * log.info(cert.toString()); } log.info(ks.size() + " certificates added to
	 * the keystore"); OutputStream outStream = new FileOutputStream(new
	 * File("dave_keystore")); ks.store(outStream,
	 * "dave_keystore".toCharArray()); } catch (Exception e) {
	 * log.error(e.toString()); } } catch (Exception e) {
	 * log.error(e.toString()); }
	 * 
	 * return retStr; }
	 */// ////////////////////////////////////OK STOP IGNORING
	// NOW//////////////////////////////////////////
	
	
	
	public Long getReviewStatus(String contentId)
			throws QueueException {
		log.debug("Returning review status for content: " + contentId);

		List matchingItems = dao.findByExample(new ContentReviewItem(contentId));
		
		if (matchingItems.size() == 0) {
			log.debug("Content " + contentId + " has not been queued previously");
			throw new QueueException("Content " + contentId + " has not been queued previously");
		}

		if (matchingItems.size() > 1)
			log.debug("more than one matching item found - using first item found");

		return ((ContentReviewItem) matchingItems.iterator().next()).getStatus();
	}
	
	
	

	public Date getDateQueued(String contentId)
			throws QueueException {
		log.debug("Returning date queued for content: " + contentId);

		List matchingItems = dao.findByExample(new ContentReviewItem(contentId));
		if (matchingItems.size() == 0) {
			log.debug("Content " + contentId + " has not been queued previously");
			throw new QueueException("Content " + contentId + " has not been queued previously");
		}

		if (matchingItems.size() > 1)
			log.debug("more than one matching item found - using first item found");

		return ((ContentReviewItem) matchingItems.iterator().next()).getDateQueued();
	}
	
	
	

	public Date getDateSubmitted(String contentId)
		throws QueueException, SubmissionException {
		log.debug("Returning date queued for content: " + contentId);

		List matchingItems = dao.findByExample(new ContentReviewItem(contentId));
		
		if (matchingItems.size() == 0) {
			log.debug("Content " + contentId + " has not been queued previously");
			throw new QueueException("Content " + contentId + " has not been queued previously");
		}

		if (matchingItems.size() > 1)
			log.debug("more than one matching item found - using first item found");

		ContentReviewItem item = (ContentReviewItem) matchingItems.iterator().next();
		if (item.getDateSubmitted() == null) {
			log.debug("Content not yet submitted: " + item.getStatus());
			throw new SubmissionException("Content not yet submitted: " + item.getStatus());
		}

		return item.getDateSubmitted();
	}

	
	
	
	
	private String encodeParam(String name, String value, String boundary) {
		return "--" + boundary + "\r\nContent-Disposition: form-data; name=\""
				+ name + "\"\r\n\r\n" + value + "\r\n";
	}



	private void createClass(String siteId) throws SubmissionException{
    	
		log.debug("Creating class for site: " + siteId);
		
    	String cpw = defaultClassPassword;
    	String ctl = siteId;
    	String diagnostic = "0";
    	String encrypt = "0";	
    	String fcmd = "2";
    	String fid = "2";
    	String uem = defaultInstructorEmail;
		String ufn = defaultInstructorFName;
		String uln = defaultInstructorLName;
		String utp = "2"; 					//user type 2 = instructor
		String upw = defaultInstructorPassword;
		String cid = siteId;
		String uid = defaultInstructorId;
		
		String gmtime = this.getGMTime();
		    	
    	// MD5 of function 2 - Create a class under a given account (instructor only)
		String md5_str = aid + cid + cpw + ctl + diagnostic + encrypt + fcmd + fid +
						 gmtime + said + uem + ufn + uid + uln + upw + utp + secretKey;
		
		String md5;
		try{
			md5 = this.getMD5(md5_str);
		} catch (Throwable t) {
			throw new SubmissionException("Cannot generate MD5 hash for Turnitin API call", t);
		}
		
		HttpsURLConnection connection;
		
		try {
			URL hostURL = new URL(apiURL);
			connection = (HttpsURLConnection) hostURL.openConnection();

			connection.setRequestMethod("GET");
			connection.setDoOutput(true);
			connection.setDoInput(true);

			log.debug("HTTPS Connection made to TII");

			OutputStream outStream = connection.getOutputStream();
		
			outStream.write("uid=".getBytes("UTF-8"));
			outStream.write(uid.getBytes("UTF-8"));
			
			outStream.write("&cid=".getBytes("UTF-8"));
			outStream.write(cid.getBytes("UTF-8"));
			
			outStream.write("&aid=".getBytes("UTF-8"));
			outStream.write(aid.getBytes("UTF-8"));			
	
			outStream.write("&cpw=".getBytes("UTF-8"));
			outStream.write(cpw.getBytes("UTF-8"));
	
			outStream.write("&ctl=".getBytes("UTF-8"));
			outStream.write(ctl.getBytes("UTF-8"));
	
			outStream.write("&diagnostic=".getBytes("UTF-8"));
			outStream.write(diagnostic.getBytes("UTF-8"));
			
			outStream.write("&encrypt=".getBytes("UTF-8"));
			outStream.write(encrypt.getBytes("UTF-8"));
	
			outStream.write("&fcmd=".getBytes("UTF-8"));
			outStream.write(fcmd.getBytes("UTF-8"));
			
			outStream.write("&fid=".getBytes("UTF-8"));
			outStream.write(fid.getBytes("UTF-8"));
			
			outStream.write("&gmtime=".getBytes("UTF-8"));
			outStream.write(gmtime.getBytes("UTF-8"));
			
			outStream.write("&said=".getBytes("UTF-8"));
			outStream.write(said.getBytes("UTF-8"));
			
			outStream.write("&uem=".getBytes("UTF-8"));
			outStream.write(uem.getBytes("UTF-8"));
			
			outStream.write("&ufn=".getBytes("UTF-8"));
			outStream.write(ufn.getBytes("UTF-8"));
			
			outStream.write("&uln=".getBytes("UTF-8"));
			outStream.write(uln.getBytes("UTF-8"));
			
			outStream.write("&upw=".getBytes("UTF-8"));
			outStream.write(upw.getBytes("UTF-8"));
			
			outStream.write("&utp=".getBytes("UTF-8"));
			outStream.write(utp.getBytes("UTF-8"));
			
			outStream.write("&md5=".getBytes("UTF-8"));
			outStream.write(md5.getBytes("UTF-8"));
			
			outStream.close();
		}
		catch (Throwable t) {
			throw new SubmissionException("Class creation call to Turnitin API failed", t);
		}
		
		
		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot get Turnitin response. Assuming call was unsuccessful", t);
		}
			
		DOMParser parser = new DOMParser();
											
		try {
			parser.parse(new org.xml.sax.InputSource(in));
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", t);
		}
	
		Document document = parser.getDocument();
		Element root = document.getDocumentElement();
		if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("20") == 0 || 
			((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("21") == 0 ) {
			log.debug("Create Class successful");						
		} else {
			throw new SubmissionException("Create Class not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
		}
    }
	
	
	private String getAssignmentTitle(String taskId){
		try {
			Reference ref = entityManager.newReference(taskId);
			log.debug("got ref " + ref + " of type: " + ref.getType());
			EntityProducer ep = ref.getEntityProducer();
		
			Entity ent = ep.getEntity(ref);
			log.debug("got entity " + ent);
			if (ent instanceof Assignment) {
				Assignment as = (Assignment)ent;
				log.debug("Got assignemment with title " + as.getTitle());
				return as.getTitle();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return taskId;
		
	}
	private void createAssignment(String siteId, String taskId) throws SubmissionException {
		
		//get the assignement reference
		String taskTitle = getAssignmentTitle(taskId);
		log.debug("Creating assignment for site: " + siteId + ", task: " + taskId +" tasktitle: " + taskTitle);
    	
    	String diagnostic = "0"; //0 = off; 1 = on
		
		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat.getDateInstance());
		dform.applyPattern("yyyyMMdd");
		Calendar cal = Calendar.getInstance();
		String dtstart = dform.format(cal.getTime());
		
		
		//set the due dates for the assignments to be in 5 month's time
		//turnitin automatically sets each class end date to 6 months after it is created
		//the assignment end date must be on or before the class end date
		
		//TODO use the 'secret' function to change this to longer
		cal.add(Calendar.MONTH, 5);
		String dtdue = dform.format(cal.getTime());
		
		String encrypt = "0";					//encryption flag
		String fcmd = "2";						//new assignment
		String fid = "4";						//function id
		String uem = defaultInstructorEmail;
		String ufn = defaultInstructorFName;
		String uln = defaultInstructorLName;
		String utp = "2"; 					//user type 2 = instructor
		String upw = defaultInstructorPassword;
		
		String cid = siteId;
		String uid = defaultInstructorId;
		String assignid = taskId;
		String assign = taskTitle;
		String ctl = siteId;
		
		String gmtime = getGMTime();
		    	
		String md5_str = aid + assign + assignid + cid + ctl + diagnostic + dtdue + dtstart + encrypt +
						 fcmd + fid + gmtime + said + uem + ufn + uid + uln + upw + utp + secretKey;
		
		String md5;
		try{
			md5 = this.getMD5(md5_str);
		} catch (Throwable t) {
			throw new SubmissionException("Could not generate MD5 hash for \"Create Assignment\" Turnitin API call");
		}
		
		HttpsURLConnection connection;
		
		try {
			URL hostURL = new URL(apiURL);
			connection = (HttpsURLConnection) hostURL.openConnection();

			connection.setRequestMethod("GET");
			connection.setDoOutput(true);
			connection.setDoInput(true);

			log.debug("HTTPS connection made to TII");

			OutputStream outStream = connection.getOutputStream();
		
    		outStream.write("aid=".getBytes("UTF-8"));
			outStream.write(aid.getBytes("UTF-8"));
			
			outStream.write("&assign=".getBytes("UTF-8"));
			outStream.write(assign.getBytes("UTF-8"));
			
			outStream.write("&assignid=".getBytes("UTF-8"));
			outStream.write(assignid.getBytes("UTF-8"));
			
			outStream.write("&cid=".getBytes("UTF-8"));
			outStream.write(cid.getBytes("UTF-8"));
			
			outStream.write("&uid=".getBytes("UTF-8"));
			outStream.write(uid.getBytes("UTF-8"));
			
			outStream.write("&ctl=".getBytes("UTF-8"));
			outStream.write(ctl.getBytes("UTF-8"));	
			
			outStream.write("&diagnostic=".getBytes("UTF-8"));
			outStream.write(diagnostic.getBytes("UTF-8"));
			
			outStream.write("&dtdue=".getBytes("UTF-8"));
			outStream.write(dtdue.getBytes("UTF-8"));
			
			outStream.write("&dtstart=".getBytes("UTF-8"));
			outStream.write(dtstart.getBytes("UTF-8"));
			
			outStream.write("&encrypt=".getBytes("UTF-8"));
			outStream.write(encrypt.getBytes("UTF-8"));
			
			outStream.write("&fcmd=".getBytes("UTF-8"));
			outStream.write(fcmd.getBytes("UTF-8"));
			
			outStream.write("&fid=".getBytes("UTF-8"));
			outStream.write(fid.getBytes("UTF-8"));
			
			outStream.write("&gmtime=".getBytes("UTF-8"));
			outStream.write(gmtime.getBytes("UTF-8"));
			
			outStream.write("&said=".getBytes("UTF-8"));
			outStream.write(said.getBytes("UTF-8"));
			
			outStream.write("&uem=".getBytes("UTF-8"));
			outStream.write(uem.getBytes("UTF-8"));
			
			outStream.write("&ufn=".getBytes("UTF-8"));
			outStream.write(ufn.getBytes("UTF-8"));
			
			outStream.write("&uln=".getBytes("UTF-8"));
			outStream.write(uln.getBytes("UTF-8"));
			
			outStream.write("&upw=".getBytes("UTF-8"));
			outStream.write(upw.getBytes("UTF-8"));
			
			outStream.write("&utp=".getBytes("UTF-8"));
			outStream.write(utp.getBytes("UTF-8"));
			
			outStream.write("&md5=".getBytes("UTF-8"));
			outStream.write(md5.getBytes("UTF-8"));
				
			outStream.close();
		}
		catch (Throwable t) {
			throw new SubmissionException("Assignment creation call to Turnitin API failed", t);
		}
		
		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot get Turnitin response. Assuming call was unsuccessful", t);
		}
		
		DOMParser parser = new DOMParser();
		
		try {
			parser.parse(new org.xml.sax.InputSource(in));
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful");
		}
		
		Document document = parser.getDocument();
		Element root = document.getDocumentElement();
		int rcode = new Integer(((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim()).intValue();
		if ((rcode > 0 && rcode < 100) || rcode == 419) {
			log.debug("Create Assignment successful");						
		} else {
			log.debug("Assignment creation failed with message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
			throw new SubmissionException("Create Assignment not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
		}
    }
	
	
	
	private void enrollInClass(String userId, String siteId) throws SubmissionException {

		
		
    	String ctl = siteId; 			//class title
    	String fid = "3";
		String fcmd = "2";
		String encrypt = "0";
		String diagnostic = "0";
		String tem = defaultInstructorEmail;
		
		User user;
		try {
			user = userDirectoryService.getUser(userId);
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot get user information", t);
		}
		
		log.debug("Enrolling user " + user.getEid() + "(" + userId + ")  in class " + siteId);
		
		String uem = user.getEmail();
		if (uem == null) {
			throw new SubmissionException ("User has no email address");
		}
		
		String ufn = user.getFirstName();
		if (ufn == null) {
			throw new SubmissionException ("User has no first name");
		}
		
		String uln = user.getLastName();
		if (uln == null) {
			throw new SubmissionException ("User has no last name");
		}
		
		String utp = "1";
		
		String uid = userId;
		String cid = siteId;
				
		String gmtime = this.getGMTime();
		
		String md5_str = aid + cid + ctl + diagnostic + encrypt + fcmd + fid + gmtime + said + tem + uem +
		 				 ufn + uid + uln + utp + secretKey;
		
		String md5;
		try{
			md5 = this.getMD5(md5_str);
		} catch (Throwable t) {
			throw new SubmissionException("Cannot generate MD5 hash for Class Enrollment Turnitin API call", t);
		}
		
		HttpsURLConnection connection;
		
		try {
			URL hostURL = new URL(apiURL);
			connection = (HttpsURLConnection) hostURL.openConnection();

			connection.setRequestMethod("GET");
			connection.setDoOutput(true);
			connection.setDoInput(true);

			log.debug("Connection made to TII");

			OutputStream outStream = connection.getOutputStream();
		
			outStream.write("fid=".getBytes("UTF-8"));
			outStream.write(fid.getBytes("UTF-8"));
			
			outStream.write("&fcmd=".getBytes("UTF-8"));
			outStream.write(fcmd.getBytes("UTF-8"));
			
			outStream.write("&cid=".getBytes("UTF-8"));
			outStream.write(cid.getBytes("UTF-8"));
			
			outStream.write("&tem=".getBytes());
			outStream.write(tem.getBytes("UTF-8"));
			
			outStream.write("&ctl=".getBytes());
			outStream.write(ctl.getBytes("UTF-8"));
			
			outStream.write("&encrypt=".getBytes());
			outStream.write(encrypt.getBytes("UTF-8"));
			
			outStream.write("&aid=".getBytes("UTF-8"));
			outStream.write(aid.getBytes("UTF-8"));
			
			outStream.write("&said=".getBytes("UTF-8"));
			outStream.write(said.getBytes("UTF-8"));
			
			outStream.write("&diagnostic=".getBytes("UTF-8"));
			outStream.write(diagnostic.getBytes("UTF-8"));
			
			outStream.write("&uem=".getBytes("UTF-8"));
			outStream.write(URLEncoder.encode(uem, "UTF-8").getBytes("UTF-8"));
			
			outStream.write("&ufn=".getBytes("UTF-8"));
			outStream.write(ufn.getBytes("UTF-8"));
			
			outStream.write("&uln=".getBytes("UTF-8"));
			outStream.write(uln.getBytes("UTF-8"));
			
			outStream.write("&utp=".getBytes("UTF-8"));
			outStream.write(utp.getBytes("UTF-8"));
			
			outStream.write("&gmtime=".getBytes("UTF-8"));
			outStream.write(URLEncoder.encode(gmtime, "UTF-8").getBytes("UTF-8"));
			
			outStream.write("&md5=".getBytes("UTF-8"));
			outStream.write(md5.getBytes("UTF-8"));
			
			outStream.write("&uid=".getBytes("UTF-8"));
			outStream.write(uid.getBytes("UTF-8"));
			
			outStream.close();
		}
		catch (Throwable t) {
			throw new SubmissionException("Student Enrollment call to Turnitin failed", t);
		}
		
		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot get Turnitin response. Assuming call was unsuccessful", t);
		}
		
		DOMParser parser = new DOMParser();
											
		try {
			parser.parse(new org.xml.sax.InputSource(in));
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", t);
		}
				
		Document document = parser.getDocument();
		Element root = document.getDocumentElement();
		if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("30") == 0 || 
			((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("31") == 0 ) {
			log.debug("Enrollment in Class successful");						
		} else {
			throw new SubmissionException("Enrollment in Class not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
		}
    }


	
	public void processQueue() {
		log.debug("Processing submission queue");

		ContentReviewItem searchItem = new ContentReviewItem();
		searchItem.setContentId(null);
		searchItem.setStatus(ContentReviewItem.NOT_SUBMITTED_CODE);

		List notSubmittedItems = dao.findByExample(searchItem);
		searchItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
		notSubmittedItems.addAll(dao.findByExample(searchItem));
		
		log.debug("Total list is now " +  notSubmittedItems.size());
		Iterator notSubmittedIterator = notSubmittedItems.iterator();
		ContentReviewItem currentItem;

		while (notSubmittedIterator.hasNext()) {
			currentItem = (ContentReviewItem) notSubmittedIterator.next();
			
			log.debug("Attempting to submit content: " + currentItem.getContentId() + " for user: " + currentItem.getUserId() + " and site: " + currentItem.getSiteId());
			
			
			User user;
			
			try {
				user = userDirectoryService.getUser(currentItem.getUserId());
			} catch (UserNotDefinedException e1) {
				log.debug("Submission attempt unsuccessful - User not found: " + e1.getMessage());
				currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE);
				dao.update(currentItem);
				continue;
			}

			
			String uem = user.getEmail().trim();
			log.debug("got email of " + uem);
			if (uem == null || uem.equals("")) {
				log.debug("Submission attempt unsuccessful - User has no email address");
				currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE);
				currentItem.setLastError("user has no email");
				dao.update(currentItem);
				continue;
			}
			
			String ufn = user.getFirstName();
			if (ufn == null) {
				log.debug("Submission attempt unsuccessful - User has no first name");
				currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE);
				currentItem.setLastError("has no first name");
				dao.update(currentItem);
				continue;
			}
			
			String uln = user.getLastName();
			if (uln == null) {
				log.debug("Submission attempt unsuccessful - User has no last name");
				currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE);
				currentItem.setLastError("has no last name");
				dao.update(currentItem);
				continue;
			}
			
			try {				
				createClass(currentItem.getSiteId());
			} catch (Throwable t) {
				log.debug ("Submission attempt unsuccessful: Could not create class", t);
				
				if (t.getClass() == IOException.class) {
					currentItem.setLastError("Class creation error: " + t.getMessage());
					currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
				} else {
					currentItem.setLastError("Class creation error: " + t.getMessage());
					currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE);
				}
				dao.update(currentItem);
				continue;
			}
			
			try {
				enrollInClass(currentItem.getUserId(), currentItem.getSiteId());
			} catch (Throwable t) {
				log.debug ("Submission attempt unsuccessful: Could not enroll user in class", t);
				
				if (t.getClass() == IOException.class) {
					currentItem.setLastError("Enrolment error: " + t.getMessage());
					currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
				} else {
					currentItem.setLastError("Enrolment error: " + t.getMessage());
					currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
				}
				dao.update(currentItem);
				continue;
			}
			
			try {
				createAssignment(currentItem.getSiteId(), currentItem.getTaskId());
			} catch (Throwable t) {
				log.debug ("Submission attempt unsuccessful: Could not create assignment");
				
				if (t.getClass() == IOException.class) {
					currentItem.setLastError("Assign creation error: " + t.getMessage());
					currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
				} else {
					//this is a to be expected error
					currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE);
					currentItem.setLastError(t.getMessage());
				}
				
				dao.update(currentItem);
				continue;
			}
			
			
			//get all the info for the api call
			//we do this before connecting so that if there is a problem we can jump out - saves time
			//these errors should probably be caught when a student is enrolled in a class
			//but we check again here to be sure
			
			
			
			String diagnostic = "0";
			String encrypt = "0";
			String fcmd = "2";
			String fid = "5";
			
			//to get the name of the initial submited file we need the title
			ContentResource resource = null;
			ResourceProperties resourceProperties = null;
			String fileName = null;
			try {
				resource = contentHostingService.getResource(currentItem.getContentId());
				resourceProperties = resource.getProperties();
				fileName = resourceProperties.getProperty(resourceProperties.getNamePropDisplayName());
				log.debug("origional filename is: " + fileName);
				if (fileName == null) {
					//use the id 
					fileName  = currentItem.getContentId();
				} else if (fileName.length() > 199) {
					fileName = fileName.substring(0, 199);
				}
			}
			catch (PermissionException e2) {
				log.debug("Submission failed due to permission error: " + e2.getMessage());
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_NO_RETRY_CODE);
				currentItem.setLastError(e2.getMessage());
				dao.update(currentItem);
				continue;
			} 
			catch (IdUnusedException e4) {
				log.debug("Submission failed due to content ID error: " + e4.getMessage());
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_NO_RETRY_CODE);
				currentItem.setLastError(e4.getMessage());
				dao.update(currentItem);
				continue;
			}
			catch (TypeException e) {
				log.debug("Submission failed due to content Type error: " + e.getMessage());
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_NO_RETRY_CODE);
				currentItem.setLastError(e.getMessage());
				dao.update(currentItem);
				continue;
			}
				
				
			String ptl = currentItem.getUserId()  + ":" + fileName;
			String ptype = "2";

			String tem = defaultInstructorEmail;

			String utp = "1";

			String uid = currentItem.getUserId();
			String cid = currentItem.getSiteId();
			String assignid = currentItem.getTaskId();

			String assign = getAssignmentTitle(currentItem.getTaskId());;
			String ctl = currentItem.getSiteId();

			String gmtime = this.getGMTime();

			String md5_str = aid + assign + assignid + cid + ctl
						+ diagnostic + encrypt + fcmd + fid + gmtime + ptl
						+ ptype + said + tem + uem + ufn + uid + uln + utp
						+ secretKey;

			String md5;
			try{
				md5 = this.getMD5(md5_str);
			} catch (NoSuchAlgorithmException e) {
				log.debug("Submission attempt failed due to MD5 generation error");
				currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
				currentItem.setLastError("MD5 error");
				dao.update(currentItem);
				continue;
			}
			
			String boundary = "";
			OutputStream outStream = null;
			
			HttpsURLConnection connection;
			
			try {
				
				URL hostURL = new URL(apiURL);
				connection = (HttpsURLConnection) hostURL.openConnection();

				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setDoInput(true);

				Random rand = new Random();
				//make up a boundary that should be unique
				boundary = Long.toString(rand.nextLong(), 26)
					+ Long.toString(rand.nextLong(), 26)
					+ Long.toString(rand.nextLong(), 26);
			
				// set up the connection to use multipart/form-data
				connection.setRequestProperty("Content-Type","multipart/form-data; boundary=" + boundary);

				log.debug("HTTPS connection made to TII");

				outStream = connection.getOutputStream();

				// connection.connect();
				outStream.write(encodeParam("assignid", assignid, boundary).getBytes());
				outStream.write(encodeParam("uid", uid, boundary).getBytes());
				outStream.write(encodeParam("cid", cid, boundary).getBytes());
				outStream.write(encodeParam("aid", aid, boundary).getBytes());
				outStream.write(encodeParam("assign", assign, boundary).getBytes());
				outStream.write(encodeParam("ctl", ctl, boundary).getBytes());
				outStream.write(encodeParam("diagnostic", diagnostic, boundary).getBytes());
				outStream.write(encodeParam("encrypt", encrypt, boundary).getBytes());
				outStream.write(encodeParam("fcmd", fcmd, boundary).getBytes());
				outStream.write(encodeParam("fid", fid, boundary).getBytes());
				outStream.write(encodeParam("gmtime", gmtime, boundary).getBytes());
				outStream.write(encodeParam("ptype", ptype, boundary).getBytes());
				outStream.write(encodeParam("ptl", ptl, boundary).getBytes());
				outStream.write(encodeParam("said", said, boundary).getBytes());
				outStream.write(encodeParam("tem", tem, boundary).getBytes());
				outStream.write(encodeParam("uem", uem, boundary).getBytes());
				outStream.write(encodeParam("ufn", ufn, boundary).getBytes());
				outStream.write(encodeParam("uln", uln, boundary).getBytes());
				outStream.write(encodeParam("utp", utp, boundary).getBytes());
				outStream.write(encodeParam("md5", md5, boundary).getBytes());

				// put in the actual file
				
				outStream.write(("--" + boundary
								+ "\r\nContent-Disposition: form-data; name=\"pdata\"; filename=\""
								+ currentItem.getContentId() + "\"\r\n"
								+ "Content-Type: " + resource.getContentType()
								+ "\r\ncontent-transfer-encoding: binary" + "\r\n\r\n")
								.getBytes());

				outStream.write(resource.getContent());
				outStream.write("\r\n".getBytes("UTF-8"));

				outStream.write(("--" + boundary + "--").getBytes());

				outStream.close();
			} catch (IOException e1) {
				log.debug("Submission failed due to IO error: " + e1.getMessage());
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
				currentItem.setLastError(e1.getMessage());
				dao.update(currentItem);
				continue;
			} 
			catch (ServerOverloadException e3) {
				log.debug("Submission failed due to server error: " + e3.getMessage());
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
				currentItem.setLastError(e3.getMessage());
				dao.update(currentItem);
				continue;
			}

			BufferedReader in;
			try {
				in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			} catch (IOException e1) {
				log.debug("Unable to determine Submission status due to response IO error: " + e1.getMessage() + ". Assume unsuccessful");
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
				dao.update(currentItem);
				continue;
			}

			DOMParser parser = new DOMParser();
												
			try {
				parser.parse(new org.xml.sax.InputSource(in));
			} catch (SAXException e) {
				log.debug("Unable to determine Submission status due to response parsing error: " + e.getMessage() + ". Assume unsuccessful");
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
				dao.update(currentItem);
				continue;
			} catch (IOException e) {
				log.debug("Unable to determine Submission status due to response IO error: " + e.getMessage() + ". Assume unsuccessful");
				currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
				dao.update(currentItem);
				continue;
			}
			
			Document document = parser.getDocument();
			Element root = document.getDocumentElement();
			if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("51") == 0) {
				log.debug("Submission successful");
				currentItem.setExternalId(((CharacterData) (root.getElementsByTagName("objectID").item(0).getFirstChild())).getData().trim());
				currentItem.setStatus(ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE);
				currentItem.setDateSubmitted(new Date());
				dao.update(currentItem);
			} else {
				log.debug("Submission not successful: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim());
				currentItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_NO_RETRY_CODE);
				currentItem.setLastError(((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim());
				dao.update(currentItem);
			}
		}

		log.debug("Submission queue processed");
	}

	private String getGMTime() {
		// calculate function2 data
		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat
				.getDateInstance());
		dform.applyPattern("yyyyMMddHH");
		dform.setTimeZone(TimeZone.getTimeZone("GMT"));
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

		String gmtime = dform.format(cal.getTime());
		gmtime += Integer.toString(((int) Math.floor((double) cal
				.get(Calendar.MINUTE) / 10)));

		return gmtime;
	}

	private String getMD5(String md5_string) throws NoSuchAlgorithmException {

		MessageDigest md = MessageDigest.getInstance("MD5");

		md.update(md5_string.getBytes());

		// convert the binary md5 hash into hex
		String md5 = "";
		byte[] b_arr = md.digest();

		for (int i = 0; i < b_arr.length; i++) {
			// convert the high nibble
			byte b = b_arr[i];
			b >>>= 4;
			b &= 0x0f; // this clears the top half of the byte
			md5 += Integer.toHexString(b);

			// convert the low nibble
			b = b_arr[i];
			b &= 0x0F;
			md5 += Integer.toHexString(b);
		}

		return md5;
	}

	public void checkForReports() {
		// get the list of all items that are waiting for reports
		List awaitingReport = dao.findByProperties(ContentReviewItem.class,
				new String[] { "status" },
				new Object[] { ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE});
		
		awaitingReport.addAll(dao.findByProperties(ContentReviewItem.class,
													new String[] { "status" },
													new Object[] { ContentReviewItem.REPORT_ERROR_RETRY_CODE}));
		
		Iterator listIterator = awaitingReport.iterator();
		HashMap reportTable = new HashMap();

		log.debug("There are " + awaitingReport.size() + " submissions awaiting reports");
		
		ContentReviewItem currentItem;
		while (listIterator.hasNext()) {
			currentItem = (ContentReviewItem) listIterator.next();

			if (!reportTable.containsKey(currentItem.getExternalId())) {
				// get the list from turnitin and see if the review is available
				
				log.debug("Attempting to update hashtable with reports for site " + currentItem.getSiteId());

				String diagnostic = "0";
				String encrypt = "0";
				String fcmd = "2";
				String fid = "10";

				String tem = defaultInstructorEmail;

				String uem = defaultInstructorEmail;
				String ufn = defaultInstructorFName;
				String uln = defaultInstructorLName;
				String utp = "2";

				String uid = defaultInstructorId;
				String cid = currentItem.getSiteId();
				String assignid = currentItem.getTaskId();

				String assign = currentItem.getTaskId();
				String ctl = currentItem.getSiteId();

				String gmtime = this.getGMTime();
				
				String md5_str = aid + assign + assignid + cid + ctl
							+ diagnostic + encrypt + fcmd + fid + gmtime + said
							+ tem + uem + ufn + uid + uln + utp + secretKey;
					
				String md5;
				try{
					md5 = this.getMD5(md5_str);
				} catch (NoSuchAlgorithmException e) {
					log.debug("Update failed due to MD5 generation error");
					currentItem.setStatus(ContentReviewItem.REPORT_ERROR_NO_RETRY_CODE);
					currentItem.setLastError("to MD5 generation error");
					dao.update(currentItem);
					listIterator.remove();
					break;
				}

				HttpsURLConnection connection;
				
				try {
					URL hostURL = new URL(apiURL);
					connection = (HttpsURLConnection) hostURL.openConnection();

					connection.setRequestMethod("GET");
					connection.setDoOutput(true);
					connection.setDoInput(true);

					log.debug("HTTPS connection made to TII");

					OutputStream out = connection.getOutputStream();

					out.write("fid=".getBytes("UTF-8"));
					out.write(fid.getBytes("UTF-8"));
					
					out.write("&fcmd=".getBytes("UTF-8"));
					out.write(fcmd.getBytes("UTF-8"));
					
					out.write("&uid=".getBytes("UTF-8"));
					out.write(uid.getBytes("UTF-8"));
					
					out.write("&tem=".getBytes("UTF-8"));
					out.write(tem.getBytes("UTF-8"));
					
					out.write("&assign=".getBytes("UTF-8"));
					out.write(assign.getBytes("UTF-8"));
					
					out.write("&assignid=".getBytes("UTF-8"));
					out.write(assignid.getBytes("UTF-8"));
					
					out.write("&cid=".getBytes("UTF-8"));
					out.write(cid.getBytes("UTF-8"));
					
					out.write("&ctl=".getBytes("UTF-8"));
					out.write(ctl.getBytes("UTF-8"));
					
					out.write("&encrypt=".getBytes());
					out.write(encrypt.getBytes("UTF-8"));
					
					out.write("&aid=".getBytes("UTF-8"));
					out.write(aid.getBytes("UTF-8"));
					
					out.write("&said=".getBytes("UTF-8"));
					out.write(said.getBytes("UTF-8"));
					
					out.write("&diagnostic=".getBytes("UTF-8"));
					out.write(diagnostic.getBytes("UTF-8"));
					
					out.write("&uem=".getBytes("UTF-8"));
					out.write(URLEncoder.encode(uem, "UTF-8").getBytes("UTF-8"));
					
					out.write("&ufn=".getBytes("UTF-8"));
					out.write(ufn.getBytes("UTF-8"));
					
					out.write("&uln=".getBytes("UTF-8"));
					out.write(uln.getBytes("UTF-8"));
					
					out.write("&utp=".getBytes("UTF-8"));
					out.write(utp.getBytes("UTF-8"));
					
					out.write("&gmtime=".getBytes("UTF-8"));
					out.write(URLEncoder.encode(gmtime, "UTF-8").getBytes("UTF-8"));
					
					out.write("&md5=".getBytes("UTF-8"));
					out.write(md5.getBytes("UTF-8"));
					
					out.close();
				} catch (IOException e) {
					log.debug("Update failed due to IO error: " + e.getMessage());
					currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
					currentItem.setLastError(e.getMessage());
					dao.update(currentItem);
					break;
				}
				
				BufferedReader in;
				try{
					in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				} catch (IOException e) {
					log.debug("Update failed due to IO error: " + e.getMessage());
					currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
					currentItem.setLastError(e.getMessage());
					dao.update(currentItem);
					break;
				}
			
		    	DOMParser parser = new DOMParser();
													
				try{
					parser.parse(new InputSource(in));
				} catch (SAXException e1) {
					log.debug("Update failed due to Parsing error: " + e1.getMessage());
					log.debug(in);
					currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
					currentItem.setLastError(e1.getMessage());
					dao.update(currentItem);
					break;
				} catch (IOException e2) {
					log.debug("Update failed due to IO error: " + e2.getMessage());
					currentItem.setStatus(ContentReviewItem.REPORT_ERROR_RETRY_CODE);
					currentItem.setLastError(e2.getMessage());
					dao.update(currentItem);
					break;
				}
				
				Document document = parser.getDocument();
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
							reportTable.put(objectId, new Integer(overlap));
						} else {
							reportTable.put(objectId, new Integer(-1));
						}
						
						log.debug("objectId: " + objectId + " similarity: " + similarityScore + " overlap: " + overlap);
					}
				} else {
					log.debug("Report list request not successful");
					log.debug(document.toString());

				}
			}
			
			int reportVal;
			// check if the report value is now there (there may have been a
			// failure to get the list above)
			if (reportTable.containsKey(currentItem.getExternalId())) {
				reportVal = ((Integer) (reportTable.get(currentItem
						.getExternalId()))).intValue();
				log.debug("reportVal for " + currentItem.getExternalId() + ": " + reportVal);
				if (reportVal != -1) {
					currentItem.setReviewScore(reportVal);
					currentItem
							.setStatus(ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE);
					currentItem.setDateReportReceived(new Date());
					dao.update(currentItem);
					log.debug("new report received: " + currentItem.getExternalId() + " -> " + currentItem.getReviewScore());
				}
			}
		}
	}
	
	public List getReportList(String siteId, String taskId) {
		log.debug("Returning list of reports for site: " + siteId + ", task: " + taskId);
		return dao.findByExample(new ContentReviewItem(null, siteId, taskId, null, null, ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE));
	}
	
	public List getReportList(String siteId) {
		log.debug("Returning list of reports for site: " + siteId);
		return dao.findByExample(new ContentReviewItem(null, siteId, null, null, null, ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE));
	}
	
	public String getServiceName() {
		return this.SERVICE_NAME;
	}
	
	public void resetUserDetailsLockedItems(String userId) {
		ContentReviewItem searchItem = new ContentReviewItem();
		searchItem.setContentId(null);
		searchItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE);
		searchItem.setUserId(userId);
		List lockedItems = dao.findByExample(searchItem);
		for (int i =0; i < lockedItems.size();i++) {
			ContentReviewItem thisItem = (ContentReviewItem) lockedItems.get(i);
			thisItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
			dao.update(thisItem);
		}
	}
	
	public String getIconUrlforScore(Long score) {
		
		String urlBase = "/sakai-content-review-tool/images/score_";
		String sufix = ".gif";
		
		if (score.equals(new Long(0))) {
			return urlBase + "blue" + sufix;
		} else if (score.compareTo(new Long(25)) < 0 ) {
			return urlBase + "green" + sufix;
		} else if (score.compareTo(new Long(50)) < 0  ) {
			return urlBase + "yellow" + sufix;
		} else if (score.compareTo(new Long(75)) < 0 ) {
			return urlBase + "orange" + sufix;
		} else {
			return urlBase + "red" + sufix;
		}
		
	}
	
	
	public boolean isAcceptableContent(ContentResource resource) {
		//for now we accept all content
		return true;
	}
	
	public boolean isSiteAcceptable(Site s) {
		return true;
	}
}
