package org.sakaiproject.contentreview.entity;
/**
 * External representation of a content review item for EB use
 * @author dhorwitz
 *
 */
public class ExternalContentReviewitem {

	
	private String userEid;
	private String assignmentReference;
	private String contentUrl;
	private String siteId;
	private String mimeType;
	private String fileName;
	
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public String getMimeType() {
		return mimeType;
	}
	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}
	public String getUserEid() {
		return userEid;
	}
	public void setUserEid(String userEid) {
		this.userEid = userEid;
	}
	public String getAssignmentReference() {
		return assignmentReference;
	}
	public void setAssignmentReference(String assignmentReference) {
		this.assignmentReference = assignmentReference;
	}
	public String getContentUrl() {
		return contentUrl;
	}
	public void setContentUrl(String contentUrl) {
		this.contentUrl = contentUrl;
	}
	public String getSiteId() {
		return siteId;
	}
	public void setSiteId(String siteId) {
		this.siteId = siteId;
	}
	
	
}
