package org.sakaiproject.contentreview.entity;

import java.util.Date;

/**
 * A POJO to expose the results of a content review item 
 * in EB
 * @author dhorwitz
 *
 */
public class ContentReviewResult {
	
	
	private String userEid;
	private String taskId;
	private String siteId;
	
	private Date dateQueued;
	private Date dateReportReceived;
	private Long status;
	private String reviewScore;
	private String ErrorCode;
	private String ErrorMessage;
	private String reportUrl;
	private Long id;
	
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getReportUrl() {
		return reportUrl;
	}
	public void setReportUrl(String reportUrl) {
		this.reportUrl = reportUrl;
	}
	public String getUserEid() {
		return userEid;
	}
	public void setUserEid(String userEid) {
		this.userEid = userEid;
	}
	public String getTaskId() {
		return taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	public String getSiteId() {
		return siteId;
	}
	public void setSiteId(String siteId) {
		this.siteId = siteId;
	}
	public Date getDateQueued() {
		return dateQueued;
	}
	public void setDateQueued(Date dateQueued) {
		this.dateQueued = dateQueued;
	}
	public Date getDateReportReceived() {
		return dateReportReceived;
	}
	public void setDateReportReceived(Date dateReportReceived) {
		this.dateReportReceived = dateReportReceived;
	}
	public Long getStatus() {
		return status;
	}
	public void setStatus(Long status) {
		this.status = status;
	}
	public String getReviewScore() {
		return reviewScore;
	}
	public void setReviewScore(String reviewScore) {
		this.reviewScore = reviewScore;
	}
	public String getErrorCode() {
		return ErrorCode;
	}
	public void setErrorCode(String errorCode) {
		ErrorCode = errorCode;
	}
	public String getErrorMessage() {
		return ErrorMessage;
	}
	public void setErrorMessage(String errorMessage) {
		ErrorMessage = errorMessage;
	}
	
	
	
}
