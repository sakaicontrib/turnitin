package org.sakaiproject.turnitin.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONObject;
import org.sakaiproject.basiclti.util.SakaiBLTIUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import org.tsugi.basiclti.BasicLTIUtil;
import org.tsugi.lti2.LTI2Vars;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.lti.api.LTIRoleAdvisor;
import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.site.api.SiteService;

import org.sakaiproject.turnitin.api.TurnitinLTIAPI;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

/**
 * This is a utility class for wrapping the LTI calls to the TurnItIn Service.
 * 
 * @author bgarcia
 *
 */
public class TurnitinLTIUtil implements TurnitinLTIAPI {
	private static final Log log = LogFactory.getLog(TurnitinLTIUtil.class);
	
	private static final Log apiTraceLog = LogFactory.getLog("org.sakaiproject.turnitin.util.TurnitinLTIUtil.apicalltrace");

	public static final int BASIC_ASSIGNMENT = 0;
	public static final int EDIT_ASSIGNNMENT = 1;
	public static final int INFO_ASSIGNNMENT = 2;
	public static final int SUBMIT = 3;
	public static final int RESUBMIT = 4;
	public static final int INFO_SUBMISSION = 5;
	
	private static final String basicAssignmentUrl = "assignment";
	private static final String editAssignmentUrl = "assignment/edit/";//assignment_id
	private static final String infoAssignmentUrl = "resource_link_tool/";//assignment_id
	private static final String submitUrl = "upload/submit/";//assignment_id
	private static final String resubmitUrl = "upload/resubmit/";//submission_id
	private static final String infoSubmissionUrl = "outcome_tool_data/";//submission_id
	
	private String aid = null;
	private String secret = null;
	private String globalId = null;
	private String globalReportsId = null;
	private String endpoint = null;
	private String turnitinSite = null;
	private String turnitinReportsSite = null;

	private static LTIRoleAdvisor ltiRoleAdvisor = null;

	private static final String SUCCESS_TEXT = "fullsuccess";
	
	private LTIService ltiService;
	public void setLtiService(LTIService ltiService) {
		this.ltiService = ltiService;
	}
	
	private SecurityService securityService;
	public void setSecurityService(SecurityService securityService) {
		this.securityService = securityService;
	}

	private ServerConfigurationService serverConfigurationService;
	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	private SiteService siteService;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}
	
	public void init() {
		log.debug("init - TurnitinLTIUtil");
		if(ltiService == null){
			log.warn("TurnitinLTIUtil: Could not find LTI service.");
		}
		turnitinSite = serverConfigurationService.getString("turnitin.lti.site", "!turnitin");
		if(StringUtils.isEmpty(turnitinSite)){
			log.error("Turnitin global site property does not exist or is wrongly configured.");
		}
		turnitinReportsSite = serverConfigurationService.getString("turnitin.reports.lti.site", "!turnitin_reports");
		if (StringUtils.isEmpty(turnitinReportsSite)){
			log.error("TurnitinReports global site property does not exist or is wrongly configured.");
		}

		endpoint = serverConfigurationService.getString("turnitin.ltiURL", "https://sandbox.turnitin.com/api/lti/1p0/");
		if(StringUtils.isEmpty(endpoint)){
			log.error("Turnitin LTI endpoint property does not exist or is wrongly configured.");
		}
		// If we're being asked to create it.
		if (serverConfigurationService.getBoolean("turnitin.lti.globalCreate", false)) {
			addGlobalTurnitinLTIToolData();
		}

		// This bean is not explicitly a singleton, so it could be instantiated/init'd multiple times?
		// ltiRoleAdvisor has been declared as static, and this "== null" check will ensure that only one will be instantiated in memory
		if (ltiRoleAdvisor == null)
		{
			final SecurityService SECURITY_SERVICE = securityService;
			final SiteService SITE_SERVICE = siteService;
			LTIRoleAdvisor turnitinRoleAdvisor = (String userId, String context, String ltiSiteId)->(
				SECURITY_SERVICE.unlock("asn.grade", SITE_SERVICE.siteReference(context)) ? LTI2Vars.MEMBERSHIP_ROLE_INSTRUCTOR : LTI2Vars.MEMBERSHIP_ROLE_LEARNER
			);
			ltiService.registerLTIRoleAdvisor(turnitinSite, turnitinRoleAdvisor);
			ltiService.registerLTIRoleAdvisor(turnitinReportsSite, turnitinRoleAdvisor);
		}
	}
	
	public TurnitinReturnValue makeLTIcall(int type, String urlParam, Map<String, String> ltiProps){
		TurnitinReturnValue retVal = new TurnitinReturnValue();
		Map<String, String> origLtiProps = ltiProps;
		if(!obtainGlobalTurnitinLTIToolData()){
			log.error("makeLTIcall - Turnitin global LTI tool does not exist or properties are wrongly configured.");
			retVal.setResult( -9 );
			retVal.setErrorMessage( "TII global LTI tool doesn't exist or properties are wrongly configured" );
			return retVal;
		}
		try {
	        
			HttpClientParams httpParams = new HttpClientParams();
			httpParams.setConnectionManagerTimeout(60000);
			HttpClient client = new HttpClient();
			client.setParams(httpParams);
			client.getParams().setParameter("http.protocol.content-charset", "UTF-8");
			
			Map<String,String> extra = new HashMap<> ();
			
			String defUrl = formUrl(type, urlParam);
			if(defUrl == null){
				log.error("makeLTIcall: type " + type + " is not correct");
				retVal.setResult( -1 );
				retVal.setErrorMessage( "Type is not correct; type = " + type );
				return retVal;
			}
			
			PostMethod method = new PostMethod(defUrl);
			ltiProps = cleanUpProperties(ltiProps);
			ltiProps = BasicLTIUtil.signProperties(ltiProps, defUrl, "POST", aid, secret, null, null, null, null, null, extra);
			if(ltiProps == null){
				log.error("Error while signing TII LTI properties.");
				retVal.setResult( -2 );
				retVal.setErrorMessage( "Error while signing TII LTI properties" );
				return retVal;
			}
			
			for (Entry<String, String> entry : ltiProps.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if (value == null)
					continue;
				method.addParameter(key,value);
			}
			
			int statusCode = client.executeMethod(method);
			if(statusCode == 400){
				String errorMessage = parseSubmissionXMLResponse( method.getResponseBodyAsString() );
				log.error("Status 400: Bad request: " + defUrl + "; message: " + errorMessage);
				log.warn("LTI props " + ltiProps.toString());
				retVal.setResult( -3 );
				retVal.setErrorMessage( errorMessage );
				return retVal;
			} else if(statusCode == 200){
				log.debug("Status 200: OK request: " + defUrl);
				if(method.getResponseBodyAsString().contains("api_errorblock")){//TODO only way since TII does not return xml response
					log.warn("LTI props " + ltiProps.toString());
					log.error(method.getResponseBodyAsString());
				} else {
					log.debug("LTI props " + ltiProps.toString());
					log.debug(method.getResponseBodyAsString());
				}
				if(type == SUBMIT || type == RESUBMIT){
					String result = parseSubmissionXMLResponse(method.getResponseBodyAsString());
					if(result != null){
						log.error("Error while submitting. " + result + " LTI props " + ltiProps.toString());
						origLtiProps.put("returnedError", result);
						retVal.setResult( -6 );
						retVal.setErrorMessage( result );
						return retVal;
					}

					retVal.setResult( 1 );
					return retVal;
				} else if(type == INFO_SUBMISSION){
					//move this?
					String jsonResponse = method.getResponseBodyAsString();
					JSONObject json = new JSONObject(jsonResponse);
					JSONObject originality = json.getJSONObject("outcome_originalityreport");
					log.debug("Originality data: " + originality.toString());
					String text = originality.getString("text");//null when no reports set on turniting, catch?
					log.debug("Originality text value: " + text);
					if(text != null && text.equals("Pending")){
						retVal.setResult( -7 );
						retVal.setErrorMessage( "Report is still pending for paper" );
						return retVal;
					}
					JSONObject numeric = originality.getJSONObject("numeric");
					int score = numeric.getInt("score");
					log.debug("Originality score value: " + score);
					if(score > 0){//show log
						JSONObject breakdown = originality.getJSONObject("breakdown");
						log.debug("Breakdown originality values: " + breakdown.toString());//TODO when should this be showed?
					}

					retVal.setResult( score );
					return retVal;
				} else if (type == BASIC_ASSIGNMENT) {
					// If we get a 200 back and don't want more information it's ok.
					retVal.setResult( 1 );
					return retVal;
				}
			} else if(statusCode == 302){
				log.debug("Successful call: " + defUrl);
				log.debug("LTI props " + ltiProps.toString());
				log.debug(method.getResponseBodyAsString());
				retVal.setResult( 1 );
				return retVal;
			} else {
				log.warn("Not controlled status: " + statusCode + " - " + method.getStatusText());
				origLtiProps.put("returnedError", statusCode + " - " + method.getStatusText());
				log.debug("LTI props " + ltiProps.toString());
				log.debug(method.getResponseBodyAsString());
			}
		
		} catch (Exception e) {
			log.error("Exception while making TII LTI call " + e.getMessage(), e);
			retVal.setResult( -4 );
			retVal.setErrorMessage( "Exception while making TII LTI call " + e.getMessage() );
			return retVal;
		}

		retVal.setResult( -5 );
		retVal.setErrorMessage( "Other LTI error" );
		return retVal;
	}
	
	public boolean obtainGlobalTurnitinLTIToolData(){
		log.debug("Setting global TII LTI tool properties");
		List<Map<String, Object>> tools = ltiService.getToolsDao("lti_tools.site_id = '"+turnitinSite+"'", null, 0, 0, turnitinSite);
		if ( tools == null || tools.size() != 1 ) {
			if(tools == null){
				log.warn("No tools found");
			} else {
				log.warn("Found: " + tools.size());
			}
			log.error("obtainGlobalTurnitinLTIToolData: wrong global TII LTI tool configuration");
			return false;
		}
		Map<String,Object> tool  = tools.get(0);
		globalId = String.valueOf(tool.get(LTIService.LTI_ID));
		log.debug("Global tool id: " + globalId);
		aid = String.valueOf(tool.get(LTIService.LTI_CONSUMERKEY));
		log.debug("Global tool key: " + aid);
		secret = String.valueOf(tool.get(LTIService.LTI_SECRET));
		log.debug("Global tool secret: " + secret);
		return !(globalId == null || aid == null || secret == null);
	}

	public void addGlobalTurnitinLTIToolData() {
		log.debug("Creating global TII LTI tool");
		if (obtainGlobalTurnitinLTIToolData()) {
			log.debug("TII LTI tool already exists.");
			return;
		}
		Map<String, Object> props = new HashMap<>();
		// TODO validate it's set
		String key = serverConfigurationService.getString("turnitin.aid");
		String secret = serverConfigurationService.getString("turnitin.lti.globalCreate.secretKey");
		secret = SakaiBLTIUtil.encryptSecret(secret.trim());

		props.put(LTIService.LTI_SITE_ID, turnitinSite);
		props.put(LTIService.LTI_TITLE, "Turnitin");
		props.put(LTIService.LTI_ALLOWTITLE, 0);
		props.put(LTIService.LTI_PAGETITLE, "Turnitin");
		props.put(LTIService.LTI_ALLOWPAGETITLE, 0);
		props.put(LTIService.LTI_STATUS, 0);
		props.put(LTIService.LTI_VISIBLE, 0);
		props.put(LTIService.LTI_LAUNCH, endpoint+ "assignment");
		props.put(LTIService.LTI_ALLOWLAUNCH, 1);
		props.put(LTIService.LTI_CONSUMERKEY, key);
		props.put(LTIService.LTI_ALLOWCONSUMERKEY, 0);
		props.put(LTIService.LTI_SECRET, secret);
		props.put(LTIService.LTI_ALLOWSECRET, 0);
		props.put(LTIService.LTI_SENDEMAILADDR, 1);
		props.put(LTIService.LTI_SENDNAME, 1);
		props.put(LTIService.LTI_ALLOWOUTCOMES, 1);
		props.put(LTIService.LTI_PL_ASSESSMENTSELECTION, 1);
		props.put(LTIService.LTI_NEWPAGE, 0);
		props.put(LTIService.LTI_DEBUG, 0);
		props.put(LTIService.LTI_CUSTOM, 1);

		Object result = ltiService.insertToolDao(props, turnitinSite, true, true);
		if (result instanceof String) {
			log.warn("Failed to add TII LTI tool: "+ result);
		} else if (result instanceof Long) {
			log.info("Added global TII LTI tool: "+ result);
		}
	}
	
	public String getGlobalTurnitinLTIToolId(){
		log.debug("Setting global TII LTI tool id");
		List<Map<String, Object>> tools = ltiService.getToolsDao("lti_tools.site_id = '"+turnitinSite+"'", null, 0, 0, turnitinSite);
		if ( tools == null || tools.size() != 1 ) {
			if(tools == null)
				log.warn("No tools found");
			else
				log.warn("Found: " + tools.size());				
			log.error("getGlobalTurnitinLTIToolId: wrong global TII LTI tool configuration");
			return null;
		}
		Map<String,Object> tool  = tools.get(0);
		globalId = String.valueOf(tool.get(LTIService.LTI_ID));
		log.debug("Global tool id: " + globalId);
		
		return globalId;
	}

	public String getGlobalTurnitinReportsLTIToolId()
	{
		log.debug("Setting global TII Reports LTI tool Id");
		List<Map<String, Object>> tools = ltiService.getToolsDao("lti_tools.site_id = '" + turnitinReportsSite + "'", null, 0, 0, turnitinReportsSite);
		if (tools == null || tools.size() != 1)
		{
			log.warn(tools == null ? "No tools found" : "Found: " + tools.size());
			log.error("getGlobalTurnitinReportsLTIToolId: wrong global TII LTI tool configured");
			return null;
		}
		Map<String, Object> tool = tools.get(0);
		globalReportsId = String.valueOf(tool.get(ltiService.LTI_ID));
		log.debug("Global reports tool id: " + globalReportsId);

		return globalReportsId;
	}

	public String getGlobalSecret() {
		return secret;
	}

	public String getGlobalKey() {
		return aid;
	}
	
	public Object insertTIIToolContent(String globalToolId, Properties props){
		if(ltiService == null){
			log.error("insertTIIToolContent: Could not find LTI service.");
			return null;
		}
		return ltiService.insertToolContent(null, globalToolId, props, "!admin");
	}
	
	public Map<String, Object> getTIIToolContent(String contentKey){
		if(ltiService == null){
			log.error("insertTIIToolContent: Could not find LTI service.");
			return null;
		}
		return ltiService.getContent(Long.valueOf(contentKey), "!admin");
	}
	
	public Object updateTIIToolContent(String contentKey, Properties content){
		if(ltiService == null){
			log.error("insertTIIToolContent: Could not find LTI service.");
			return null;
		}
		return ltiService.updateContent(Long.valueOf(contentKey), content, "!admin");
	}
	
	public boolean deleteTIIToolContent(String contentKey){
		if(ltiService == null){
			log.error("deleteTIIToolContent: Could not find LTI service.");
			return false;
		}
		return ltiService.deleteContent(Long.valueOf(contentKey));
	}
	
	private String formUrl(int type, String urlParam){
		switch(type){
			case BASIC_ASSIGNMENT:
				return endpoint+basicAssignmentUrl;
			case EDIT_ASSIGNNMENT:
				return endpoint+editAssignmentUrl+urlParam;
			case INFO_ASSIGNNMENT:
				return endpoint+infoAssignmentUrl+urlParam;
			case SUBMIT:
				return endpoint+submitUrl+urlParam;
			case RESUBMIT:
				return endpoint+resubmitUrl+urlParam;
			case INFO_SUBMISSION:
				return endpoint+infoSubmissionUrl+urlParam;
			default:
				return null;
		}
	}
	
	private String parseSubmissionXMLResponse(String xml){
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try{
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(new InputSource(new StringReader(xml)));
			Element doc = document.getDocumentElement();
			String status = doc.getElementsByTagName("status").item(0).getChildNodes().item(0).getNodeValue();
			if(status.equals(SUCCESS_TEXT)){
				String tiiSubmissionId = doc.getElementsByTagName("lis_result_sourcedid").item(0).getChildNodes().item(0).getNodeValue();
				log.debug("TII submission id: " + tiiSubmissionId);
			} else {
				String errorMessage = doc.getElementsByTagName("message").item(0).getChildNodes().item(0).getNodeValue();
				log.error("Error when submitting to TII: " + errorMessage);//TODO return the error and store it?
				return errorMessage;
			}
		} catch(ParserConfigurationException | SAXException | IOException | DOMException ee){
			log.error("Could not parse TII response: " + ee.getMessage());
			return ee.getMessage();
		} catch(Exception e){
			log.error( "Unexpected exception parsing XML response", e );
			return e.getMessage();
		}
		return null;
	}

	Map<String, String> cleanUpProperties(Map<String, String> rawProperties) {
		for (Iterator<String> iterator = rawProperties.keySet().iterator(); iterator.hasNext(); ) {
			String okey = iterator.next();
			String key = okey.trim();
			String value = rawProperties.get(key);
			if (value == null || "".equals(value)) {
				// remove null or empty values
				iterator.remove();
			}
		}
		return rawProperties;
	}
	
	public Set<String> getSitesUsingLTI()
	{
		List<Map<String, Object>> contents = ltiService.getContentsDao(null, null, 0, 0, null, true);
		Set<String> tiiSiteIds = new HashSet<>();
		for (Map<String, Object> map : contents)
		{
			String title = map.get("pagetitle").toString();
			if ("Turnitin".equals(title))
			{
				String siteId = map.get("SITE_ID").toString();
				if (StringUtils.isNotBlank(siteId))
				{
					tiiSiteIds.add(siteId);
				}
			}
		}
		return tiiSiteIds;
	}
}
