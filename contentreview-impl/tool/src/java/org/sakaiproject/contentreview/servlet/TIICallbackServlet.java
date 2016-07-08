package org.sakaiproject.contentreview.servlet;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import org.tsugi.json.IMSJSONRequest;
import org.sakaiproject.assignment.api.AssignmentContent;
import org.sakaiproject.assignment.api.AssignmentContentEdit;
import org.sakaiproject.assignment.cover.AssignmentService;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityAdvisor.SecurityAdvice;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.basiclti.util.SakaiBLTIUtil;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;

/** 
 * This servlet will receive callbacks from TII. Then it will process the data
 * related to the assignment and store it.
 */

@SuppressWarnings("deprecation")
public class TIICallbackServlet extends HttpServlet {
	
	private static Log M_log = LogFactory.getLog(TIICallbackServlet.class);
	
	private LTIService ltiService;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		M_log.debug("init TIICallbackServlet");
		ltiService = (LTIService) ComponentManager.get(LTIService.class);
		Objects.requireNonNull(ltiService);
		super.init(config);
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		M_log.debug("doGet TIICallbackServlet");
		doPost(request, response);
	}
	
	@SuppressWarnings("unchecked")
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		M_log.debug("doPost TIICallbackServlet");
		String ipAddress = request.getRemoteAddr();
		M_log.debug("Service request from IP=" + ipAddress);
		
		String allowOutcomes = ServerConfigurationService.getString(SakaiBLTIUtil.BASICLTI_OUTCOMES_ENABLED, SakaiBLTIUtil.BASICLTI_OUTCOMES_ENABLED_DEFAULT);
		if ( ! "true".equals(allowOutcomes) ) allowOutcomes = null;

		if (allowOutcomes == null ) {
			M_log.warn("LTI Services are disabled IP=" + ipAddress);
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		
		String contentType = request.getContentType();
		if ( contentType != null && contentType.startsWith("application/json") ) {
			doPostJSON(request, response);
		} else {
			M_log.warn("TIICallbackServlet received a not json call. Callback should have a Content-Type header of application/json.");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	
	@SuppressWarnings("unchecked")
    protected void doPostJSON(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException 
    {
		M_log.debug("doPostJSON TIICallbackServlet");
		
		IMSJSONRequest jsonRequest = new IMSJSONRequest(request);
		if ( jsonRequest.valid ) {
			M_log.debug(jsonRequest.getPostBody());
		}
		
		String turnitinSite = ServerConfigurationService.getString("turnitin.lti.site", "!turnitin");
		Map<String,Object> tiiData = ServletUtils.obtainGlobalTurnitinLTITool(turnitinSite);
		if(tiiData == null){
			M_log.error("Turnitin global LTI tool does not exist or properties are wrongly configured.");
		}
		String key = String.valueOf(tiiData.get(LTIService.LTI_CONSUMERKEY));
		String secret = String.valueOf(tiiData.get(LTIService.LTI_SECRET));
		
		// Lets check the signature
		if ( key == null || secret == null ) {
			M_log.debug("doPostJSON Deployment is missing credentials");
			response.setStatus(HttpServletResponse.SC_FORBIDDEN); 
			doErrorJSON(request, response, jsonRequest, "Deployment is missing credentials", null);
			return;
		}

		jsonRequest.validateRequest(key, secret, request);
		if ( !jsonRequest.valid ) {
			M_log.debug("doPostJSON OAuth signature failure");
			response.setStatus(HttpServletResponse.SC_FORBIDDEN); 
			doErrorJSON(request, response, jsonRequest, "OAuth signature failure", null);
			return;
		}

		JSONObject json = new JSONObject(jsonRequest.getPostBody());
		//M_log.debug(json.toString());
		
		String assignmentId = json.getString("resource_link_id");
		int tiiId = json.getInt("assignmentid");
		//ext_resource_tool_placement_url parameter can also be processed if necessary
		SecurityService securityService = (SecurityService) ComponentManager.get(SecurityService.class);
		SecurityAdvisor yesMan = (String userId, String function, String reference)->{return SecurityAdvice.ALLOWED;};
		try{
			securityService.pushAdvisor(yesMan);
			AssignmentContentEdit ace = AssignmentService.editAssignmentContent(assignmentId);
			ResourcePropertiesEdit aPropertiesEdit = ace.getPropertiesEdit();
			aPropertiesEdit.addProperty("turnitin_id", String.valueOf(tiiId));
			AssignmentService.commitEdit(ace);
		}catch(Exception e){
			M_log.error("Could not find assignment with content id " + assignmentId + " or store the TII assignment id: " + e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		finally
		{
			securityService.popAdvisor(yesMan);
		}

        return;
    }
	
	public void doErrorJSON(HttpServletRequest request,HttpServletResponse response, 
			IMSJSONRequest json, String message, Exception e) 
		throws java.io.IOException 
	{
		if (e != null) {
			M_log.error(e.getLocalizedMessage(), e);
		}
        M_log.info(message);
		String output = IMSJSONRequest.doErrorJSON(request, response, json, message, e);
		M_log.warn(output);
    }
	
}
