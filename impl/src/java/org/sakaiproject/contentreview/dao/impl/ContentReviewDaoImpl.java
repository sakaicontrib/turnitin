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

package org.sakaiproject.contentreview.dao.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.contentreview.model.ContentReviewLock;
import org.sakaiproject.genericdao.hibernate.HibernateCompleteGenericDao;
import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;

/**
 * Implementations of any specialized DAO methods from the specialized DAO 
 * that allows the developer to extend the functionality of the generic dao package
 * @author Sakai App Builder -AZ
 */
public class ContentReviewDaoImpl 
	extends HibernateCompleteGenericDao 
		implements ContentReviewDao {

	private static Log log = LogFactory.getLog(ContentReviewDaoImpl.class);

	public void init() {
		log.debug("init");
		try{
			super.initDao();
		} catch (Exception e) {
			log.error(e);
		}
	}

	/**
	    * Allows a lock to be obtained that is system wide,
	    * this is primarily for ensuring something runs on a single server only in a cluster<br/>
	    * <b>NOTE:</b> This intentionally returns a null on failure rather than an exception since exceptions will
	    * cause a rollback which makes the current session effectively dead, this also makes it impossible to 
	    * control the failure so instead we return null as a marker
	    * 
	    * @param lockId the name of the lock which we are seeking
	    * @param holderId a unique id for the holder of this lock (normally a server id)
	    * @param timePeriod the length of time (in milliseconds) that the lock should be valid for,
	    * set this very low for non-repeating processes (the length of time the process should take to run)
	    * and the length of the repeat period plus the time to run the process for repeating jobs
	    * @return true if a lock was obtained, false if not, null if failure
	    */
	   @SuppressWarnings("unchecked")
	   public Boolean obtainLock(String lockId, String executerId, long timePeriod) {
		   log.debug("getting lock for " + lockId + " for: " + executerId);
	      if (executerId == null || 
	            "".equals(executerId)) {
	         throw new IllegalArgumentException("The executer Id must be set");
	      }
	      if (lockId == null || 
	            "".equals(lockId)) {
	         throw new IllegalArgumentException("The lock Id must be set");
	      }

	      // basically we are opening a transaction to get the current lock and set it if it is not there
	      Boolean obtainedLock = false;
	      try {
	         // check the lock
	         List<ContentReviewLock> locks = findByProperties(ContentReviewLock.class, 
	               new String[] {"name"},
	               new Object[] {lockId});
	         if (locks.size() > 0) {
	            // check if this is my lock, if not, then exit, if so then go ahead
	            ContentReviewLock lock = locks.get(0);
	            if (lock.getHolder().equals(executerId)) {
	               obtainedLock = true;
	               // if this is my lock then update it immediately
	               lock.setLastModified(new Date());
	               getHibernateTemplate().save(lock);
	               getHibernateTemplate().flush(); // this should commit the data immediately
	            } else {
	               // not the lock owner but we can still get the lock
	               long validTime = lock.getLastModified().getTime() + timePeriod + 100;
	               if (System.currentTimeMillis() > validTime) {
	                  // the old lock is no longer valid so we are taking it
	                  obtainedLock = true;
	                  lock.setLastModified(new Date());
	                  lock.setHolder(executerId);
	                  getHibernateTemplate().save(lock);
	                  getHibernateTemplate().flush(); // this should commit the data immediately
	               } else {
	                  // someone else is holding a valid lock still
	                  obtainedLock = false;
	               }
	            }
	         } else {
	            // obtain the lock
	            ContentReviewLock lock = new ContentReviewLock(lockId, executerId);
	            getHibernateTemplate().save(lock);
	            getHibernateTemplate().flush(); // this should commit the data immediately
	            obtainedLock = true;
	         }
	      } catch (RuntimeException e) {
	         obtainedLock = null; // null indicates the failure
	         cleanupLockAfterFailure(lockId);
	         log.fatal("Lock obtaining failure for lock ("+lockId+"): " + e.getMessage(), e);
	      }

	      return obtainedLock;
	   }

	   /**
	    * Releases a lock that was being held,
	    * this is useful if you know a server is shutting down and you want to release your locks early<br/>
	    * <b>NOTE:</b> This intentionally returns a null on failure rather than an exception since exceptions will
	    * cause a rollback which makes the current session effectively dead, this also makes it impossible to 
	    * control the failure so instead we return null as a marker
	    * 
	    * @param lockId the name of the lock which we are seeking
	    * @param holderId a unique id for the holder of this lock (normally a server id)
	    * @return true if a lock was released, false if not, null if failure
	    */
	   @SuppressWarnings("unchecked")
	   public Boolean releaseLock(String lockId, String executerId) {
	      if (executerId == null || 
	            "".equals(executerId)) {
	         throw new IllegalArgumentException("The executer Id must be set");
	      }
	      if (lockId == null || 
	            "".equals(lockId)) {
	         throw new IllegalArgumentException("The lock Id must be set");
	      }

	      // basically we are opening a transaction to get the current lock and set it if it is not there
	      Boolean releasedLock = false;
	      try {
	         // check the lock
	         List<ContentReviewLock> locks = findByProperties(ContentReviewLock.class, 
	               new String[] {"name"},
	               new Object[] {lockId});
	         if (locks.size() > 0) {
	            // check if this is my lock, if not, then exit, if so then go ahead
	            ContentReviewLock lock = locks.get(0);
	            if (lock.getHolder().equals(executerId)) {
	               releasedLock = true;
	               // if this is my lock then remove it immediately
	               getHibernateTemplate().delete(lock);
	               getHibernateTemplate().flush(); // this should commit the data immediately
	            } else {
	               releasedLock = false;
	            }
	         }
	      } catch (RuntimeException e) {
	         releasedLock = null; // null indicates the failure
	         cleanupLockAfterFailure(lockId);
	         log.fatal("Lock releasing failure for lock ("+lockId+"): " + e.getMessage(), e);
	      }

	      return releasedLock;
	   }
	   
	   /**
	    * Cleans up lock if there was a failure
	    * 
	    * @param lockId
	    */
	   @SuppressWarnings("unchecked")
	   private void cleanupLockAfterFailure(String lockId) {
	      getHibernateTemplate().clear(); // cancel any pending operations
	      // try to clear the lock if things died
	      try {
	         List<ContentReviewLock> locks = findByProperties(ContentReviewLock.class, 
	               new String[] {"name"},
	               new Object[] {lockId});
	         getHibernateTemplate().deleteAll(locks);
	         getHibernateTemplate().flush();
	      } catch (Exception ex) {
	         log.error("Could not cleanup the lock ("+lockId+") after failure: " + ex.getMessage(), ex);
	      }
	   }
	
}
