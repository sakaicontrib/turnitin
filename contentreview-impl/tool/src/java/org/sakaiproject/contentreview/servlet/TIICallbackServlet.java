package org.sakaiproject.contentreview.servlet;

import java.io.IOException;
import java.util.Objects;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;

import org.tsugi.json.IMSJSONRequest;
import org.sakaiproject.basiclti.util.SakaiBLTIUtil;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.contentreview.turnitin.TurnitinConstants;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.turnitin.api.TurnitinLTIAPI;

/** 
 * This servlet will receive callbacks from TII. Then it will process the data
 * related to the assignment and store it.
 */

@SuppressWarnings("deprecation")
public class TIICallbackServlet extends HttpServlet {
	
	private static Log M_log = LogFactory.getLog(TIICallbackServlet.class);
	
	private LTIService ltiService;
	private TurnitinLTIAPI turnitinLTIAPI;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		M_log.debug("init TIICallbackServlet");
		ltiService = (LTIService) ComponentManager.get(LTIService.class);
		Objects.requireNonNull(ltiService);
		turnitinLTIAPI = (TurnitinLTIAPI)ComponentManager.get(TurnitinLTIAPI.class);
		Objects.requireNonNull(turnitinLTIAPI);

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
		
		String key = turnitinLTIAPI.getGlobalKey();
		String secret = turnitinLTIAPI.getGlobalSecret();
		
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
		
		try
		{
			String asnRef = StringUtils.trimToEmpty(json.getString("resource_link_id"));
			int sepIndex = asnRef.lastIndexOf(Entity.SEPARATOR);
			String asnId = sepIndex == -1 ? asnRef : asnRef.substring(sepIndex + 1);
			String tiiAsnId = String.valueOf(json.getInt("assignmentid"));
			ContentReviewService crs = (ContentReviewService) ComponentManager.get(ContentReviewService.class);
			boolean success = crs.saveOrUpdateActivityConfigEntry(TurnitinConstants.TURNITIN_ASN_ID, tiiAsnId, asnId,
					TurnitinConstants.SAKAI_ASSIGNMENT_TOOL_ID, TurnitinConstants.PROVIDER_ID, true);
			if (!success)
			{
				M_log.error(String.format("Could not set turnitin assignment id %s for assignment with id %s", tiiAsnId, asnId));
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}
		catch (JSONException e)
		{
			M_log.error("Unable to parse required assignment data from Turnitin json response.", e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

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
