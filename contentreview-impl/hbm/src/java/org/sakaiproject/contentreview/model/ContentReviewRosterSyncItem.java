package org.sakaiproject.contentreview.model;

import java.util.Date;

/**
 * This represents a Course in Sakai that needs to be sync'ed with the 
 * corresponding in Turnitin.  A item of this type will be added to the queue
 * whenever someone updates someones role in a Sakai Site. The roster sync
 * quartz job will take one of these items, obtain a lock on it with the 
 * Content Review locking table and perform the role adjusts on the Turnitin
 * Side.
 * 
 * @author sgithens
 *
 */
public class ContentReviewRosterSyncItem {

	// Our database lookups depend on these constants so don't change them.
	public static final int NOT_STARTED_STATUS = 1;
	public static final int FAILED_STATUS = 2;
	public static final int FINISHED_STATUS = 3;
	
	private Long id;
	private Date dateQueued;
	private Date lastTried;
	private String siteId;
	private String messages;
	private int status;
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Date getDateQueued() {
		return dateQueued;
	}
	public void setDateQueued(Date dateQueued) {
		this.dateQueued = dateQueued;
	}
	public Date getLastTried() {
		return lastTried;
	}
	public void setLastTried(Date lastTried) {
		this.lastTried = lastTried;
	}
	public String getSiteId() {
		return siteId;
	}
	public void setSiteId(String siteId) {
		this.siteId = siteId;
	}
	public int getStatus() {
		return status;
	}
	public void setStatus(int status) {
		this.status = status;
	}
	public String getMessages() {
		return messages;
	}
	public void setMessages(String messages) {
		this.messages = messages;
	}
	
	
}
