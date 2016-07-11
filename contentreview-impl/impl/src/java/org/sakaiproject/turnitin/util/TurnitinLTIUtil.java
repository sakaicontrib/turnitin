package org.sakaiproject.turnitin.util;

import java.io.StringReader;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.tsugi.basiclti.BasicLTIUtil;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.lti.api.LTIService;

/**
 * This is a utility class for wrapping the LTI calls to the TurnItIn Service.
 * 
 * @author bgarcia
 *
 */
public class TurnitinLTIUtil {
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
	private String endpoint = null;
	private String turnitinSite = null;
	
	private String SUCCESS_TEXT = "fullsuccess";
	
	private LTIService ltiService;
	public void setLtiService(LTIService ltiService) {
		this.ltiService = ltiService;
	}
	
	private ServerConfigurationService serverConfigurationService;
	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
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
		endpoint = serverConfigurationService.getString("turnitin.ltiURL", "https://sandbox.turnitin.com/api/lti/1p0/");
		if(StringUtils.isEmpty(endpoint)){
			log.error("Turnitin LTI endpoint property does not exist or is wrongly configured.");
		}
	}
	
	public int makeLTIcall(int type, String urlParam, Map<String, String> ltiProps){
		Map<String, String> origLtiProps = ltiProps;
		if(!obtainGlobalTurnitinLTIToolData()){
			log.error("makeLTIcall - Turnitin global LTI tool does not exist or properties are wrongly configured.");
			return -9;
		}
		try {
	        
			HttpClientParams httpParams = new HttpClientParams();
			httpParams.setConnectionManagerTimeout(60000);
			HttpClient client = new HttpClient();
			client.setParams(httpParams);
			client.getParams().setParameter("http.protocol.content-charset", "UTF-8");
			
			Map<String,String> extra = new HashMap<String,String> ();
			
			String defUrl = formUrl(type, urlParam);
			if(defUrl == null){
				log.error("makeLTIcall: type " + type + " is not correct");
				return -1;
			}
			
			PostMethod method = new PostMethod(defUrl);
			ltiProps = cleanUpProperties(ltiProps);
			ltiProps = BasicLTIUtil.signProperties(ltiProps, defUrl, "POST", aid, secret, null, null, null, null, null, extra);
			if(ltiProps == null){
				log.error("Error while signing TII LTI properties.");
				return -2;
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
				log.warn("Status 400: Bad request: " + defUrl);
				log.warn("LTI props " + ltiProps.toString());
				return -3;
			} else if(statusCode == 200){
				log.debug("Status 200: OK request: " + defUrl);
				if(method.getResponseBodyAsString().contains("api_errorblock")){//TODO only way since TII does not return xml response
					log.warn("LTI props " + ltiProps.toString());
					log.warn(method.getResponseBodyAsString());
				} else {
					log.debug("LTI props " + ltiProps.toString());
					log.debug(method.getResponseBodyAsString());
				}
				if(type == SUBMIT || type == RESUBMIT){
					String result = parseSubmissionXMLResponse(method.getResponseBodyAsString());
					if(result != null){
						log.warn("Error while submitting. " + result + " LTI props " + ltiProps.toString());
						origLtiProps.put("returnedError", result);
						(new Exception()).printStackTrace();
						return -6;
					}
					return 1;
				} else if(type == INFO_SUBMISSION){
					//move this?
					String jsonResponse = method.getResponseBodyAsString();
					JSONObject json = new JSONObject(jsonResponse);
					JSONObject originality = json.getJSONObject("outcome_originalityreport");
					log.debug("Originality data: " + originality.toString());
					String text = originality.getString("text");//null when no reports set on turniting, catch?
					log.debug("Originality text value: " + text);
					if(text != null && text.equals("Pending")){
						return -7;
					}
					JSONObject numeric = originality.getJSONObject("numeric");
					int score = numeric.getInt("score");
					log.debug("Originality score value: " + score);
					if(score > 0){//show log
						JSONObject breakdown = originality.getJSONObject("breakdown");
						log.debug("Breakdown originality values: " + breakdown.toString());//TODO when should this be showed?
					}
					return score;
				} else if (type == BASIC_ASSIGNMENT) {
					// If we get a 200 back and don't want more information it's ok.
					return 1;
				}
			} else if(statusCode == 302){
				log.debug("Successful call: " + defUrl);
				log.debug("LTI props " + ltiProps.toString());
				log.debug(method.getResponseBodyAsString());
				return 1;
			} else {
				log.warn("Not controlled status: " + statusCode + " - " + method.getStatusText());
				origLtiProps.put("returnedError", statusCode + " - " + method.getStatusText());
				log.debug("LTI props " + ltiProps.toString());
				log.debug(method.getResponseBodyAsString());
			}
		
		} catch (Exception e) {
			log.error("Exception while making TII LTI call " + e.getMessage());
			return -4;
	    }
		
		return -5;
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
		globalId = String.valueOf(tool.get(ltiService.LTI_ID));
		log.debug("Global tool id: " + globalId);
		aid = String.valueOf(tool.get(ltiService.LTI_CONSUMERKEY));
		log.debug("Global tool key: " + aid);
		secret = String.valueOf(tool.get(ltiService.LTI_SECRET));
		log.debug("Global tool secret: " + secret);
		if(globalId == null || aid == null || secret == null){
			return false;
		} else {
			return true;
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
		globalId = String.valueOf(tool.get(ltiService.LTI_ID));
		log.debug("Global tool id: " + globalId);
		
		return globalId;
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
		} catch(Exception ee){
			log.error("Could not parse TII response: " + ee.getMessage());
			return ee.getMessage();
		}
		return null;
	}

	private Map<String, String> cleanUpProperties(Map<String, String> rawProperties) {
		for (String okey : rawProperties.keySet()) {
			String key = okey.trim();
			String value = rawProperties.get(key);
			if (value == null || "".equals(value)) {
				// remove null or empty values
				rawProperties.remove(key);
			}
		}
		return rawProperties;
	}
}
