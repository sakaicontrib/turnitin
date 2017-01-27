/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/turnitin/trunk/contentreview-impl/impl/src/java/org/sakaiproject/contentreview/impl/turnitin/TurnitinReviewServiceImpl.java $
 * $Id: TurnitinReviewServiceImpl.java 69345 2010-07-22 08:11:44Z david.horwitz@uct.ac.za $
 ***********************************************************************************
 *
 * Copyright (c) 2006 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
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
import java.util.Optional;
import org.apache.commons.lang.StringUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;
import org.sakaiproject.contentreview.exception.QueueException;
import org.sakaiproject.contentreview.exception.ReportException;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.model.ContentReviewActivityConfigEntry;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.contentreview.service.ContentReviewService;
import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.genericdao.api.search.Order;
import org.sakaiproject.genericdao.api.search.Restriction;
import org.sakaiproject.genericdao.api.search.Search;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.springframework.dao.DataIntegrityViolationException;

public abstract class BaseReviewServiceImpl implements ContentReviewService {

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
	
	protected ContentReviewSiteAdvisor siteAdvisor;
	public void setSiteAdvisor(ContentReviewSiteAdvisor crsa) {
		this.siteAdvisor = crsa;
	}

	@Override
	public void queueContent(String userId, String siteId, String taskId, List<ContentResource> content, String submissionId, boolean isResubmission)
			throws QueueException {

		if (content == null || content.size() < 1) {
			return;
		}

		for (ContentResource contentRes : content)
		{
			try
			{
				queueContent(userId, siteId, taskId, contentRes.getId(), submissionId, isResubmission);
			}
			catch (QueueException qe)
			{
				// QueueException is thrown if this content item is already queued. This will be a problem for
				// a multiple attachments + resubmission scenario where a new file is added to an
				// already queued submission. Log but ignore the exception if this might be the case, and continue on to the 
				// next item. Otherwise, allow the exception to bubble up.
				if (!isResubmission || content.size() == 1)
				{
					throw qe;
				}
				
				log.info(String.format("Unable to queue content item %s for submission id %s (task: %s, site: %s). Error was: %s",
						contentRes.getId(), submissionId, taskId, siteId, qe.getMessage()));
			}
		}

	}

	public void queueContent(String userId, String siteId, String taskId, String contentId, String submissionId, boolean isResubmission)
		throws QueueException {
	
		log.debug("Method called queueContent(" + userId + "," + siteId + "," + contentId + ")");

		if (StringUtils.isBlank(userId))
		{
			throw new QueueException("Unable to queue content item " + contentId + ", a userId was not provided.");
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

		List<ContentReviewItem> existingItems = getItemsByContentId(contentId);
		if (existingItems.size() > 0) {
			throw new QueueException("Content " + contentId + " is already queued, not re-queued");
		}
		ContentReviewItem item = new ContentReviewItem(userId, siteId, taskId, contentId, new Date(),
			ContentReviewItem.NOT_SUBMITTED_CODE);
		item.setNextRetryTime(new Date());
		item.setUrlAccessed(false);
		item.setSubmissionId(submissionId);
		if(isResubmission){
			item.setResubmission(true);
		}
		dao.save(item);
	}

	protected List<ContentReviewItem> getItemsByContentId(String contentId) {
		Search search = new Search();
		search.addRestriction(new Restriction("contentId", contentId));
		List<ContentReviewItem> existingItems = dao.findBySearch(ContentReviewItem.class, search);
		return existingItems;
	}
	
	public ContentReviewItem getFirstItemByContentId(String contentId) {
		Search search = new Search();
		search.addRestriction(new Restriction("contentId", contentId));
		List<ContentReviewItem> existingItems = dao.findBySearch(ContentReviewItem.class, search);
		if (existingItems.isEmpty()) {
			log.debug("Content " + contentId + " has not been queued previously");
			return null;
		}

		if (existingItems.size() > 1){
			log.warn("More than one matching item - using first item found");
		}

		return existingItems.get(0);
	}
	
	public ContentReviewItem getFirstItemByExternalId(String externalId) {
		//due to the impossibility to get the right paper id from the turnitin callback
		//we need to get the paper id associated to the original submission
		Search search = new Search();
		search.addRestriction(new Restriction("externalId", externalId));
		search.addOrder(new Order("id", false));
		List<ContentReviewItem> existingItems = dao.findBySearch(ContentReviewItem.class, search);
		if (existingItems.isEmpty()) {
			log.debug("Content with paper id " + externalId + " has not been queued previously");
			return null;
		}

		if (existingItems.size() > 1){
			log.warn("More than one matching item - using first item found");
		}

		return existingItems.get(0);
	}
	
	public ContentReviewItem getItemById(String id) {
		Search search = new Search();
		search.addRestriction(new Restriction("id", Long.valueOf(id)));
		List<ContentReviewItem> existingItems = dao.findBySearch(ContentReviewItem.class, search);
		if (existingItems.isEmpty()) {
			log.debug("Content " + id + " has not been queued previously");
			return null;
		}

		if (existingItems.size() > 1){
			log.warn("More than one matching item - using first item found");
		}

		return existingItems.get(0);
	}
	
	public int getReviewScore(String contentId)
			throws QueueException, ReportException, Exception {
		log.debug("Getting review score for content: " + contentId);

		List<ContentReviewItem> matchingItems = getItemsByContentId(contentId);
		if (matchingItems.isEmpty()) {
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
		
		return item.getReviewScore();
	}

	
	
	public Long getReviewStatus(String contentId)
			throws QueueException {
		log.debug("Returning review status for content: " + contentId);

		List<ContentReviewItem> matchingItems = getItemsByContentId(contentId);
		
		if (matchingItems.isEmpty()) {
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

		List<ContentReviewItem> matchingItems = getItemsByContentId(contentId);
		if (matchingItems.isEmpty()) {
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

		List<ContentReviewItem> matchingItems = getItemsByContentId(contentId);
		
		if (matchingItems.isEmpty()) {
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
		Search search = new Search();
		//TII-99 siteId can be null
		if (siteId != null) {
			search.addRestriction(new Restriction("siteId", siteId));
		}
		search.addRestriction(new Restriction("taskId", taskId));
		search.addRestriction(new Restriction("status", ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE));
		
		List<ContentReviewItem> existingItems = dao.findBySearch(ContentReviewItem.class, search);
		
		
		return existingItems;
	}
	
	public List<ContentReviewItem> getAllContentReviewItems(String siteId, String taskId) {
            log.debug("Returning list of reports for site: " + siteId + ", task: " + taskId);
            Search search = new Search();
            //TII-99 siteId can be null
            if (siteId != null) {
                    search.addRestriction(new Restriction("siteId", siteId));
            }
            search.addRestriction(new Restriction("taskId", taskId));
            
            List<ContentReviewItem> existingItems = dao.findBySearch(ContentReviewItem.class, search);
            
            
            return existingItems;
    }
	
	public List<ContentReviewItem> getReportList(String siteId) {
		log.debug("Returning list of reports for site: " + siteId);
		
		Search search = new Search();
		search.addRestriction(new Restriction("siteId", siteId));
		search.addRestriction(new Restriction("status", ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE));
		
		return dao.findBySearch(ContentReviewItem.class, search);
	}
	

	
	public void resetUserDetailsLockedItems(String userId) {
		Search search = new Search();
		search.addRestriction(new Restriction("userId", userId));
		search.addRestriction(new Restriction("status", ContentReviewItem.SUBMISSION_ERROR_USER_DETAILS_CODE));
		
		
		List<ContentReviewItem> lockedItems = dao.findBySearch(ContentReviewItem.class, search);
		for (int i =0; i < lockedItems.size();i++) {
			ContentReviewItem thisItem = (ContentReviewItem) lockedItems.get(i);
			thisItem.setStatus(ContentReviewItem.SUBMISSION_ERROR_RETRY_CODE);
			dao.update(thisItem);
		}
	}
	

	public void removeFromQueue(String ContentId) {
		List<ContentReviewItem> object = getItemsByContentId(ContentId);
		dao.delete(object);
		
		
	}

	public boolean updateItemAccess(String contentId){
		return dao.updateIsUrlAccessed( contentId, true );
	}

	public boolean updateExternalId(String contentId, String externalId)
	{
		return dao.updateExternalId(contentId, externalId);
	}
		
	public boolean updateExternalGrade(String contentId, String score){
		ContentReviewItem cri = getFirstItemByContentId(contentId);
		if(cri != null){
			cri.setExternalGrade(score);
			dao.update(cri);
			return true;
		}
		return false;
	}
	
	public String getExternalGradeForContentId(String contentId){
		ContentReviewItem cri = getFirstItemByContentId(contentId);
		if(cri != null){
			return cri.getExternalGrade();
		}
		return null;
	}

	public boolean allowResubmission() {
		return true;
	}

	public boolean isSiteAcceptable(Site s) {
		throw new UnsupportedOperationException("Not implemented");
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
	
	public boolean isAcceptableSize(ContentResource resource) {
		throw new UnsupportedOperationException("Not implemented");
	}

	public String getServiceName() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isAcceptableContent(ContentResource resource) {
		throw new UnsupportedOperationException("This is not yet implemented");
	}

	public void processQueue() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getActivityConfigValue(String name, String activityId, String toolId, int providerId)
	{
		return getActivityConfigEntry(name, activityId, toolId, providerId)
				.map(e -> StringUtils.trimToEmpty(e.getValue())).orElse("");

	}
	
	private Optional<ContentReviewActivityConfigEntry> getActivityConfigEntry(String name, String activityId, String toolId, int providerId)
	{
		Search search = new Search();
		search.addRestriction(new Restriction("name", name));
		search.addRestriction(new Restriction("activityId", activityId));
		search.addRestriction(new Restriction("toolId", toolId));
		search.addRestriction(new Restriction("providerId", providerId));
		return Optional.ofNullable(dao.findOneBySearch(ContentReviewActivityConfigEntry.class, search));
	}

	@Override
	public boolean saveOrUpdateActivityConfigEntry(String name, String value, String activityId, String toolId, int providerId, boolean overrideIfSet)
	{
		if (StringUtils.isBlank(name) || StringUtils.isBlank(value) || StringUtils.isBlank(activityId) || StringUtils.isBlank(toolId))
		{
			return false;
		}
		
		Optional<ContentReviewActivityConfigEntry> optEntry = getActivityConfigEntry(name, activityId, toolId, providerId);
		if (!optEntry.isPresent())
		{
			try
			{
				dao.create(new ContentReviewActivityConfigEntry(name, value, activityId, toolId, providerId));
				return true;
			}
			catch (DataIntegrityViolationException | ConstraintViolationException e)
			{
				// there is a uniqueness constraint on entry keys in the database
				// a row with the same key was written after we checked, retrieve new data and continue
				optEntry = getActivityConfigEntry(name, activityId, toolId, providerId);
			}
		}

		if (overrideIfSet)
		{
			ContentReviewActivityConfigEntry entry = optEntry.orElseThrow( () -> new RuntimeException("Unique constraint violated during insert attempt, yet unable to retrieve row."));
			entry.setValue(value);
			dao.update(entry);
			return true;
		}

		return false;
	}
}
