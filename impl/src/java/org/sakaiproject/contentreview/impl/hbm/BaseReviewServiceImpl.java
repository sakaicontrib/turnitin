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

package org.sakaiproject.contentreview.impl.hbm;

import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;
import org.sakaiproject.contentreview.exception.QueueException;
import org.sakaiproject.contentreview.exception.ReportException;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.genericdao.api.search.Restriction;
import org.sakaiproject.genericdao.api.search.Search;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.UserDirectoryService;

public class BaseReviewServiceImpl implements ContentReviewService {

	private String defaultAssignmentName = null;

	private static final Log log = LogFactory
			.getLog(BaseReviewServiceImpl.class);

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
	
	private ContentReviewSiteAdvisor siteAdvisor;
	public void setSiteAdvisor(ContentReviewSiteAdvisor crsa) {
		this.siteAdvisor = crsa;
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
		 * 
		 * 
		 */

		List<ContentReviewItem> existingItems = dao
				.findByExample(new ContentReviewItem(contentId));
		if (existingItems.size() > 0) {
				throw new QueueException("Content " + contentId + " is already queued, not re-queued");
		}
		ContentReviewItem item = new ContentReviewItem(userId, siteId, taskId, contentId, new Date(),
				ContentReviewItem.NOT_SUBMITTED_CODE);
		item.setNextRetryTime(new Date());
		dao.save(item);
	}
	
	public int getReviewScore(String contentId)
			throws QueueException, ReportException, Exception {
		log.debug("Getting review score for content: " + contentId);

		List<ContentReviewItem> matchingItems = dao.findByExample(new ContentReviewItem(contentId));
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

	
	
	public Long getReviewStatus(String contentId)
			throws QueueException {
		log.debug("Returning review status for content: " + contentId);

		List<ContentReviewItem> matchingItems = dao.findByExample(new ContentReviewItem(contentId));
		
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

		List<ContentReviewItem> matchingItems = dao.findByExample(new ContentReviewItem(contentId));
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

		List<ContentReviewItem> matchingItems = dao.findByExample(new ContentReviewItem(contentId));
		
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
	


	public List<ContentReviewItem> getReportList(String siteId, String taskId) {
		log.debug("Returning list of reports for site: " + siteId + ", task: " + taskId);
		return dao.findByExample(new ContentReviewItem(null, siteId, taskId, null, null, ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE));
	}
	
	public List<ContentReviewItem> getReportList(String siteId) {
		log.debug("Returning list of reports for site: " + siteId);
		return dao.findByExample(new ContentReviewItem(null, siteId, null, null, null, ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE));
	}
	

	
	public void resetUserDetailsLockedItems(String userId) {
		ContentReviewItem searchItem = new ContentReviewItem();
		searchItem.setContentId(null);
		searchItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE);
		searchItem.setUserId(userId);
		List<ContentReviewItem> lockedItems = dao.findByExample(searchItem);
		for (int i =0; i < lockedItems.size();i++) {
			ContentReviewItem thisItem = (ContentReviewItem) lockedItems.get(i);
			thisItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
			dao.update(thisItem);
		}
	}
	

	public void removeFromQueue(String ContentId) {
		Search search = new Search();
		search.addRestriction(new Restriction("contentId",ContentId));
		List<ContentReviewItem> object = dao.findBySearch(ContentReviewItem.class, search);
		dao.delete(object);
		
		
	}

	public boolean isSiteAcceptable(Site s) {
		return siteAdvisor.siteCanUseReviewService(s);
	}
	

	

	
	public boolean allowResubmission() {
		return true;
	}

	public void checkForReports() {
		// TODO Auto-generated method stub
		
	}

	public String getIconUrlforScore(Long score) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getReviewReport(String contentId) throws QueueException, ReportException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getReviewReportInstructor(String contentId) throws QueueException, ReportException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getReviewReportStudent(String contentId) throws QueueException, ReportException {
		// TODO Auto-generated method stub
		return null;
	}
	
	public String getServiceName() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isAcceptableContent(ContentResource resource) {
		// TODO Auto-generated method stub
		return false;
	}

	public void processQueue() {
		// TODO Auto-generated method stub
		
	}

	


	

}
