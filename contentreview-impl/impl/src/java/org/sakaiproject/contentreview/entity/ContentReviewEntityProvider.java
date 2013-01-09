package org.sakaiproject.contentreview.entity;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;
import org.sakaiproject.contentreview.exception.QueueException;
import org.sakaiproject.contentreview.exception.ReportException;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entitybroker.DeveloperHelperService;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.CoreEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Describeable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.RESTful;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.entityprovider.search.Search;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InconsistentException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.genericdao.api.search.Restriction;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;



/**
 * 
 * @author dhorwitz
 *
 */

public class ContentReviewEntityProvider implements CoreEntityProvider, AutoRegisterEntityProvider, RESTful, Describeable {

	public final static String ENTITY_PREFIX = "contentreview";

	private static final Log log = LogFactory.getLog(ContentReviewEntityProvider.class);
	//Injected Services

	private ContentReviewService contentReviewService;	
	public void setContentReviewService(ContentReviewService contentReviewService) {
		this.contentReviewService = contentReviewService;
	}


	private ContentReviewDao dao;
	public void setDao(ContentReviewDao dao) {
		this.dao = dao;
	}


	private DeveloperHelperService developerHelperService;	
	public void setDeveloperHelperService(
			DeveloperHelperService developerHelperService) {
		this.developerHelperService = developerHelperService;
	}

	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}

	private ServerConfigurationService serverConfigurationService;	
	public void setServerConfigurationService(
			ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}


	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

	/**
	 * EB methods
	 */

	public String getEntityPrefix() {
		return ENTITY_PREFIX;
	}

	public boolean entityExists(String id) {
		log.info("entityExists(" + id);
		if (id == null) {
			return false;
		}
		if ("".equals(id)) {
			return true;
		}
		try {
			contentReviewService.getReviewStatus(id);
			return true;
		} catch (QueueException e) {
			return false;
		}


	}

	public String createEntity(EntityReference ref, Object entity,
			Map<String, Object> params) {
		ExternalContentReviewitem item = (ExternalContentReviewitem) entity;
		if (item.getUserEid() == null ) {
			throw new IllegalArgumentException("User EID must be set");
		}
		String userRef = developerHelperService.getUserRefFromUserEid(item.getUserEid());
		String userId= EntityReference.getIdFromRef(userRef);

		String currentUserReference = developerHelperService.getCurrentUserReference();
		if (currentUserReference == null) {
			throw new SecurityException("anonymous user cannot create ContentReviewItem: " + ref);
		}
		if (!isValidForThisUser(currentUserReference, userRef)) {
			throw new SecurityException("not authorized to create entity: " + ref);
		}




		String sakaiContentId = storeContentGetId(item.getContentUrl(), item.getMimeType(), item.getFileName());

		try {
			Long id = queueContent(userId, item.getSiteId(), item.getAssignmentReference(), sakaiContentId);
			return id.toString();
		} catch (QueueException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Can the current use create a entry for the following user?
	 * @param currentUserReference
	 * @param userRef
	 * @return
	 */
	private boolean isValidForThisUser(String currentUserReference,
			String userRef) {

		//are they equal?
		if (currentUserReference.equals(userRef)) {
			return true;
		}

		//Admin always can
		if (developerHelperService.isUserAdmin(currentUserReference)) {
			return true;
		}

		//we need a configurable user list that can do this for integrations
		String[] users = serverConfigurationService.getStrings("contentReview.ebSuperusers");
		List<String> userList = Arrays.asList(users);
		if (userList.contains(currentUserReference)) {
			return true;
		}

		return false;
	}

	/**
	 * Get an external piece of content store it in sakai and return the content id
	 * @param contentUrl
	 * @return
	 */
	private String storeContentGetId(String contentUrl, String mimeType, String fileName) {
		if (contentUrl == null) {
			throw new IllegalArgumentException("content url must be provided");
		}

		URL url;
		try {
			url = new URL(contentUrl);
			URLConnection con = url.openConnection();
			InputStream in = con.getInputStream();
			String encoding = con.getContentEncoding();
			encoding = encoding == null ? "UTF-8" : encoding;

			try {
				ResourceProperties props = contentHostingService.newResourceProperties();
				props.addProperty(props.getNamePropDisplayName(), fileName);
				ContentResource res = contentHostingService.addAttachmentResource("contentreview/external", mimeType, in, null);
				return res.getId();
			} catch (IdInvalidException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InconsistentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IdUsedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (PermissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (OverQuotaException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ServerOverloadException e) {
				e.printStackTrace();
				throw new RuntimeException("Unable to add content!", e);
			}

		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



		return null;
	}

	public Object getSampleEntity() {
		return new ExternalContentReviewitem();
	}

	public void updateEntity(EntityReference ref, Object entity,
			Map<String, Object> params) {
		// TODO Auto-generated method stub

	}

	public Object getEntity(EntityReference ref) {
		log.info("getEntiy(" + ref.toString());
		String id = ref.getId();
		if (id == null) {
			return new ContentReviewItem();
		}
		ContentReviewItem existingItems = getItemById(id);
		return existingItems;
	}

	private ContentReviewItem getItemById(String id) {
		//FIXME this should be a service method
		org.sakaiproject.genericdao.api.search.Search search = new org.sakaiproject.genericdao.api.search.Search();
		search.addRestriction(new Restriction("id", id));
		ContentReviewItem existingItems = dao.findOneBySearch(ContentReviewItem.class, search);
		log.info("returning!");
		return existingItems;
	}

	public void deleteEntity(EntityReference ref, Map<String, Object> params) {
		// TODO Auto-generated method stub

	}

	public List<?> getEntities(EntityReference ref,Search search) {
		// TODO Auto-generated method stub
		return null;
	}

	public String[] getHandledOutputFormats() {
		return new String[] {Formats.XML, Formats.JSON,  Formats.FORM};
	}

	public String[] getHandledInputFormats() {
		return new String[] {Formats.XML, Formats.JSON,  Formats.FORM, Formats.HTML};
	}

	@EntityCustomAction(action = "assignment", viewKey = "")
	public List<ContentReviewResult> getAssingmentObjects(EntityView entityView, Map<String, Object> params) {
		//Admin always can

		//we need a configurable user list that can do this for integrations
		String currentUserReference = developerHelperService.getCurrentUserReference();
		String[] users = serverConfigurationService.getStrings("contentReview.ebSuperusers");
		List<String> userList = new ArrayList<String>();
		if (users != null) {
			userList = Arrays.asList(users);
		}
		if (!userList.contains(currentUserReference) && !developerHelperService.isUserAdmin(currentUserReference)) {
			throw new SecurityException("User not authorized");
		}





		String siteId = null;
		if (params.containsKey("siteId")) {
			siteId = (String)params.get("siteId");
		}
		String taskId = (String)params.get("taskId");
		log.info("getting collection for site: " + siteId + " and task: " + taskId);
		List<ContentReviewItem> reports = new ArrayList<ContentReviewItem>();
		try {
			reports = contentReviewService.getAllContentReviewItems(siteId, taskId);
		} catch (QueueException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SubmissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ReportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<ContentReviewResult> ret = new ArrayList<ContentReviewResult>();

		for (int i = 0; i < reports.size(); i++) {
			ContentReviewItem item = reports.get(i);
			ContentReviewResult result = itemToResult(item);

			ret.add(result);
		}

		return ret;
	}

	private ContentReviewResult itemToResult(ContentReviewItem item) {
		ContentReviewResult result = new ContentReviewResult();

		String eid = item.getUserId();
		try {
			eid = userDirectoryService.getUserEid(item.getUserId());
		} catch (UserNotDefinedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		result.setUserEid(eid);
		result.setTaskId(item.getTaskId());
		result.setSiteId(item.getSiteId());
		Long status = item.getStatus();
		result.setStatus(status);
		if (ContentReviewItem.SUBMITTED_REPORT_AVAILABLE.equals(status)) {
			try {
				String report = contentReviewService.getReviewReportStudent(item.getContentId());
				result.setReportUrl(report);
			} catch (QueueException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ReportException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (item.getErrorCode() != null) {
			result.setErrorCode(item.getErrorCode().toString());
			result.setErrorMessage(contentReviewService.getLocalizedStatusMessage(item.getErrorCode().toString()));

		}



		result.setDateQueued(item.getDateQueued());
		result.setDateReportReceived(item.getDateReportReceived());
		return result;
	}


	@EntityCustomAction(action = "userAssignment", viewKey = "")
	public List<ContentReviewResult> getUserAssingmentObjects(EntityView entityView, Map<String, Object> params) {
		List<ContentReviewResult> ret = new ArrayList<ContentReviewResult>();
		//Admin always can

		//we need a configurable user list that can do this for integrations
		String currentUserReference = developerHelperService.getCurrentUserReference();
		String[] users = serverConfigurationService.getStrings("contentReview.ebSuperusers");
		List<String> userList = new ArrayList<String>();
		if (users != null) {
			userList = Arrays.asList(users);
		}
		if (!userList.contains(currentUserReference) && !developerHelperService.isUserAdmin(currentUserReference)) {
			throw new SecurityException("User not authorized");
		}

		String userEid= (String)params.get("userEid");
		String taskId = (String)params.get("taskId");

		List<ContentReviewItem> vals = getItemsForUserinTask(userEid, taskId);
		for (int i = 0; i < vals.size(); i++) {
			ContentReviewItem item = vals.get(i);
			ContentReviewResult result = itemToResult(item);

			ret.add(result);
		}

		return ret;
	}

	//TODO this should be a service method
	private List<ContentReviewItem> getItemsForUserinTask(String userEid, String taskId) {
		log.info("getItemsForUserinTask(" + userEid + ", " + taskId);
		org.sakaiproject.genericdao.api.search.Search search = new org.sakaiproject.genericdao.api.search.Search();
		search.addRestriction(new Restriction("taskId", taskId));

		try {
			String userId = userDirectoryService.getUserId(userEid);
			search.addRestriction(new Restriction("userId", userId));
		} catch (UserNotDefinedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new ArrayList<ContentReviewItem>();
		}

		List<ContentReviewItem>  existingItems = dao.findBySearch(ContentReviewItem.class, search);
		log.info("returning " + existingItems.size() + " results");
		return existingItems;
	}

	//TODO should be in the service
	public Long queueContent(String userId, String siteId, String taskId, String contentId)
			throws QueueException {
		log.debug("Method called queueContent(" + userId + "," + siteId + "," + contentId + ")");

		if (userId == null) {
			log.debug("Using current user");
			userId = userDirectoryService.getCurrentUser().getId();
		}

		if (siteId == null) {
			log.debug("Using current site");
			siteId = developerHelperService.getCurrentLocationId();
		}
		
		if (taskId == null) {
			log.debug("Generating default taskId");
			taskId = siteId + " " + "unkownAssignement";
		}

		log.debug("Adding content: " + contentId + " from site " + siteId
				+ " and user: " + userId + " for task: " + taskId + " to submission queue");

	
		
		
		ContentReviewItem item = new ContentReviewItem(userId, siteId, taskId, contentId, new Date(),
				ContentReviewItem.NOT_SUBMITTED_CODE);
		item.setNextRetryTime(new Date());
		dao.save(item);
		return item.getId();
	}
	
}
