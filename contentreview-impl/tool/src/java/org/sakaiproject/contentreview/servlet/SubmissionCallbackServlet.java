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
import org.sakaiproject.assignment.api.AssignmentSubmissionEdit;
import org.sakaiproject.assignment.cover.AssignmentService;
import org.sakaiproject.basiclti.util.SakaiBLTIUtil;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.event.cover.NotificationService;
import org.sakaiproject.lti.api.LTIService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;

/** 
 * This servlet will receive callbacks from TII. Then it will process the data
 * related to the submissions and resubmission and store it.
 */

@SuppressWarnings("deprecation")
public class SubmissionCallbackServlet extends HttpServlet {
	
	private static Log M_log = LogFactory.getLog(SubmissionCallbackServlet.class);
	
	private ContentReviewService contentReviewService;
	private LTIService ltiService;

	@Override
	public void init(ServletConfig config) throws ServletException {
		M_log.debug("init SubmissionCallbackServlet");
		contentReviewService = (ContentReviewService) ComponentManager.get(ContentReviewService.class);
		Objects.requireNonNull(contentReviewService);
		ltiService = (LTIService) ComponentManager.get(LTIService.class);
		Objects.requireNonNull(ltiService);
		super.init(config);
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		M_log.debug("doGet SubmissionCallbackServlet");
		doPost(request, response);
	}
	
	@SuppressWarnings("unchecked")
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		M_log.debug("doPost SubmissionCallbackServlet");
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
			M_log.warn("SubmissionCallbackServlet received a not json call. Callback should have a Content-Type header of application/json.");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	
	@SuppressWarnings("unchecked")
    protected void doPostJSON(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException 
    {
		M_log.debug("doPostJSON SubmissionCallbackServlet");
				
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
		
		String submissionId = json.getString("lis_result_sourcedid");
		int tiiPaperId = json.getInt("paperid");
		//ext_outcomes_tool_placement_url parameter can also be processed if necessary
		try{
			Session session = SessionManager.getCurrentSession();
			session.setUserId("admin");
			ContentReviewItem cri = contentReviewService.getFirstItemByContentId(submissionId);
			if(cri == null){
				M_log.debug("Could not find the content review item for content " + submissionId);
				return;
			} else {
				ContentResourceEdit cre = ContentHostingService.editResource(cri.getContentId());
				M_log.debug("Got content resource");
				ResourcePropertiesEdit aPropertiesEdit = cre.getPropertiesEdit();
				aPropertiesEdit.addProperty("turnitin_id", String.valueOf(tiiPaperId));
				ContentHostingService.commitResource(cre, NotificationService.NOTI_NONE);//TODO check
				M_log.debug("Successfully stored external id into content resource.");
				//NOTE: storing it on the submission too, resubmission process has to be revised
				AssignmentSubmissionEdit ase = AssignmentService.editSubmission(cri.getSubmissionId());
				aPropertiesEdit = ase.getPropertiesEdit();
				aPropertiesEdit.addProperty("turnitin_id", String.valueOf(tiiPaperId));
				AssignmentService.commitEditFromCallback(ase);
			}
		}catch(Exception e){
			M_log.error("Could not find submission with id " + submissionId + " or store the TII submission id: " + e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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