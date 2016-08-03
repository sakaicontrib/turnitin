package org.sakaiproject.contentreview.servlet;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.sakaiproject.turnitin.api.TurnitinLTIAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.tsugi.pox.IMSPOXRequest;
import org.sakaiproject.assignment.api.Assignment;
import org.sakaiproject.assignment.api.AssignmentContent;
import org.sakaiproject.assignment.api.AssignmentSubmissionEdit;
import org.sakaiproject.assignment.cover.AssignmentService;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityAdvisor.SecurityAdvice;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.basiclti.util.SakaiBLTIUtil;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.lti.api.LTIService;
import org.w3c.dom.DOMException;

/** 
 * This servlet will receive callbacks from TII. Then it will process the data
 * related to the grades and store it.
 */

@SuppressWarnings("deprecation")
public class GradingCallbackServlet extends HttpServlet {

	private static Log M_log = LogFactory.getLog(GradingCallbackServlet.class);

	private static final String SAK_PROP_ENABLE_GRADEMARK = "turnitin.grademark.integration.enabled";
	private static final boolean SAK_PROP_ENABLE_GRADEMARK_DEFAULT = true;
	private static final boolean GRADEMARK_INTEGRATION_ENABLED = ServerConfigurationService.getBoolean( SAK_PROP_ENABLE_GRADEMARK, SAK_PROP_ENABLE_GRADEMARK_DEFAULT );
	private static final String DEBUG_MSG_GRADEMARK_DISABLED = "TII Grademark integration disabled in sakai.properties; aborting.";

	private ContentReviewService contentReviewService;
	private LTIService ltiService;
	private TurnitinLTIAPI turnitinLTIAPI;

	@Override
	public void init(ServletConfig config) throws ServletException {
		M_log.debug("init GradingCallbackServlet");
		contentReviewService = (ContentReviewService) ComponentManager.get(ContentReviewService.class);
		Objects.requireNonNull(contentReviewService);
		ltiService = (LTIService) ComponentManager.get(LTIService.class);
		Objects.requireNonNull(ltiService);
		turnitinLTIAPI = (TurnitinLTIAPI) ComponentManager.get(TurnitinLTIAPI.class);
		Objects.requireNonNull(turnitinLTIAPI);
		super.init(config);
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if( GRADEMARK_INTEGRATION_ENABLED ) {
			M_log.debug("doGet GradingCallbackServlet");
			doPost(request, response);
		}
		else {
			M_log.debug( DEBUG_MSG_GRADEMARK_DISABLED );
		}
	}

	@SuppressWarnings("unchecked")
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if( GRADEMARK_INTEGRATION_ENABLED ) {
			M_log.debug("doPost GradingCallbackServlet");
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
			if ( contentType != null && contentType.startsWith("application/xml") ) {
				doPostXml(request, response);
			} else {
				M_log.warn("GradingCallbackServlet received a not xml call. Callback should have a Content-Type header of application/xml.");
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
		}
		else {
			M_log.debug( DEBUG_MSG_GRADEMARK_DISABLED );
		}
	}

	@SuppressWarnings("unchecked")
	protected void doPostXml(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException 
	{
		if( GRADEMARK_INTEGRATION_ENABLED ) {
			M_log.debug("doPostXml GradingCallbackServlet");
			IMSPOXRequest poxRequest = new IMSPOXRequest(request);
			if ( poxRequest.valid ) {
				M_log.debug(poxRequest.getPostBody());
			}

			String key = turnitinLTIAPI.getGlobalKey();
			String secret = turnitinLTIAPI.getGlobalSecret();

			// Lets check the signature
			if ( key == null || secret == null ) {
				M_log.debug("doPostJSON Deployment is missing credentials");
				response.setStatus(HttpServletResponse.SC_FORBIDDEN); 
				doErrorXML(request, response, poxRequest, "Deployment is missing credentials", null);
				return;
			}

			poxRequest.validateRequest(key, secret, request);
			if ( !poxRequest.valid ) {
				M_log.debug("doPostJSON OAuth signature failure");
				response.setStatus(HttpServletResponse.SC_FORBIDDEN); 
				doErrorXML(request, response, poxRequest, "OAuth signature failure", null);
				return;
			}

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			String sourcedId = null;
			SecurityService securityService = (SecurityService) ComponentManager.get(SecurityService.class);
			SecurityAdvisor yesMan = (String userId, String function, String reference)->{return SecurityAdvice.ALLOWED;};
			try{
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(new InputSource(new StringReader(poxRequest.getPostBody())));
				Element doc = document.getDocumentElement();
				sourcedId = doc.getElementsByTagName("sourcedId").item(0).getChildNodes().item(0).getNodeValue();
				String score = doc.getElementsByTagName("textString").item(0).getChildNodes().item(0).getNodeValue();//catched exception if textString is null
				//TODO if they remove grade it's considered as an exception and sakai external grade is not updated
				M_log.debug("sourcedId " + sourcedId + ", score " + score);
				securityService.pushAdvisor(yesMan);

				if(contentReviewService == null){
					M_log.warn("Can't find contentReviewService");
					return;
				}
				ContentReviewItem cri = contentReviewService.getFirstItemByContentId(sourcedId);
				if(cri == null){
					M_log.info("Could not find the content review item for content " + sourcedId);
					cri = contentReviewService.getFirstItemByExternalId(sourcedId);
				}
				if(cri == null){
					M_log.warn("Could not find the content review item for paper id " + sourcedId);
				} else {
					Assignment a = AssignmentService.getAssignment(cri.getTaskId());
					AssignmentContent ac = a.getContent();
					if(ac == null){
						M_log.debug("Could not find the assignment content " + cri.getTaskId());
						return;
					} else {
						M_log.debug("Got assignment content " + cri.getTaskId());
					}

					int factor = AssignmentService.getScaleFactor();
					int dec = (int)Math.log10(factor);
					int maxPoints = ac.getMaxGradePoint() / factor;
					if(maxPoints == 0){//assignment grades might not be numeric
						return;
					}
					double convertedScore = Double.valueOf(score)*maxPoints;
					String convertedScoreString = String.format("%." + dec + "f", convertedScore);
					M_log.debug("Maximum points: " + maxPoints + " - converted score: " + convertedScoreString);

					M_log.debug("cri " + cri.getId() + " - " + cri.getContentId());
					boolean itemUpdated = contentReviewService.updateExternalGrade(cri.getContentId(), convertedScoreString);
					if(!itemUpdated){
						M_log.error("Could not update cr item external grade for content " + cri.getContentId());
						return;
					}

					if(convertedScore >= 0){
						if(ac.getTypeOfSubmission() != 5 && ac.getTypeOfSubmission() != 1){
							M_log.debug(ac.getTypeOfSubmission() + " is the type setting for task " + cri.getTaskId());
						} else {
							AssignmentSubmissionEdit ase = AssignmentService.editSubmission(cri.getSubmissionId());
							if(ase != null){
								String assignmentGrade = ase.getGrade();
								if(StringUtils.isEmpty(assignmentGrade)){
									M_log.debug("Setting external grade as assignments grade");
									convertedScoreString = String.format("%." + dec + "f", convertedScore);
									ase.setGrade(convertedScoreString);
								} else {
									M_log.debug("Flagging submission");
									ase.setExternalGradeDifferent(Boolean.TRUE);
								}
								AssignmentService.commitEditFromCallback(ase);
							}
						}
					}
				}
			} catch(ParserConfigurationException pce){
				M_log.error("Could not parse TII response (ParserConfigurationException): " + pce.getMessage());
			} catch(SAXException se){
				M_log.error("Could not parse TII response (SAXException): " + se.getMessage());
			} catch(IOException | DOMException | IdUnusedException | PermissionException | NumberFormatException | InUseException e){
				M_log.error("Could not update the content review item " + sourcedId, e);
			}
			finally
			{
				securityService.popAdvisor(yesMan);
			}
		}
		else {
			M_log.debug( DEBUG_MSG_GRADEMARK_DISABLED );
		}
	}

	public void doErrorXML(HttpServletRequest request,HttpServletResponse response, IMSPOXRequest pox, String message, Exception e) 
		throws java.io.IOException 
	{
		if( GRADEMARK_INTEGRATION_ENABLED ) {
			if (e != null) {
				M_log.error(e.getLocalizedMessage(), e);
			}
			M_log.warn(message);
		}
		else {
			M_log.debug( DEBUG_MSG_GRADEMARK_DISABLED );
		}
	}
}
