package org.sakaiproject.contentreview.entity;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
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
			contentReviewService.queueContent(userId, item.getSiteId(), item.getAssignmentReference(), sakaiContentId);
			return "Success!";
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
		//TODO we need a configurable user list that can do this for integrations
		
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
	public List<ContentReviewItem> getAssingmentObjects(EntityView entityView, Map<String, Object> params) {
		//TODO we need a custom external POJO here
		//TODO we need some security
		String siteId = null;
		if (params.containsKey("siteId")) {
			siteId = (String)params.get("siteId");
		}
		String taskId = (String)params.get("taskId");
		log.info("getting collection for site: " + siteId + " and task: " + taskId);
		List<ContentReviewItem> ret = new ArrayList<ContentReviewItem>();
		try {
			ret = contentReviewService.getAllContentReviewItems(siteId, taskId);
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
		return ret;
	}


}
