/******************************************************************************
 * EvaluationDaoImplTest.java - created by aaronz@vt.edu
 * 
 * Copyright (c) 2007 Virginia Polytechnic Institute and State University
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 * 
 * Contributors:
 * Aaron Zeckoski (aaronz@vt.edu) - primary
 * 
 *****************************************************************************/

package org.sakaiproject.contentreview.dao;

import java.util.Date;

import org.sakaiproject.contentreview.dao.impl.ContentReviewDao;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.contentreview.model.ContentReviewLock;
import org.sakaiproject.contentreview.test.ContentReviewTestDataLoad;
import org.springframework.test.AbstractTransactionalSpringContextTests;


/**
 * Testing for the Evaluation Data Access Layer
 * 
 * @author Aaron Zeckoski (aaronz@vt.edu)
 */
public class ContentReviewDaoImplTest extends AbstractTransactionalSpringContextTests {

 

	protected ContentReviewDao contentReviewDao;

   private ContentReviewTestDataLoad etdl;

   private ContentReviewItem contentReviewItemLocked;
   private ContentReviewItem contentReviewItemUnlocked;
   private ContentReviewItem contentReviewItemLockedExp;
   private ContentReviewLock contentReviewLock;
   
   protected String[] getConfigLocations() {
      // point to the needed spring config files, must be on the classpath
      // (add component/src/webapp/WEB-INF to the build path in Eclipse),
      // they also need to be referenced in the maven file
      return new String[] {"hibernate-test.xml", "spring-hibernate.xml"};
   }

   // run this before each test starts
   protected void onSetUpBeforeTransaction() throws Exception {
      // load the spring created dao class bean from the Spring Application Context
      contentReviewDao = (ContentReviewDao) applicationContext.getBean("org.sakaiproject.contentreview.dao.ContentReviewDao");
      if (contentReviewDao == null) {
         throw new NullPointerException("DAO could not be retrieved from spring context");
      }



   }

   private static final String ADMIN_USER = "admin";
   private static final String USER = "dhorwitz";
   
   // run this before each test starts and as part of the transaction
   protected void onSetUpInTransaction() {
	   contentReviewItemLockedExp = new ContentReviewItem(USER,"site","task","content",new Date(), ContentReviewItem.NOT_SUBMITTED_CODE);
	   contentReviewDao.save(contentReviewItemLockedExp);
	   
	   //first test we have saved the item
	   assertNotNull(contentReviewItemLockedExp.getId());
	   
	   ContentReviewItem newItem = new ContentReviewItem(USER,"site","task","content",new Date(), ContentReviewItem.NOT_SUBMITTED_CODE);
	   contentReviewDao.save(newItem);
	   
	   //now this should have an id greater that is different from the one above
	   assertNotSame(newItem.getId(), contentReviewItemLockedExp.getId());
	   
	   //can we get the lock?
	   Long tId = Long.valueOf(contentReviewItemLockedExp.getId());
	   String sId = tId.toString();
	   assertTrue(contentReviewDao.obtainLock(sId, ADMIN_USER, -1000));

	   
	   //lock item
	   contentReviewItemLocked = new ContentReviewItem(USER,"site","task","content",new Date(), ContentReviewItem.NOT_SUBMITTED_CODE);
	   contentReviewDao.save(contentReviewItemLocked);
	   contentReviewDao.obtainLock(Long.valueOf(contentReviewItemLocked.getId()).toString(), ADMIN_USER, 10000);

	   contentReviewItemUnlocked = new ContentReviewItem(USER,"site","task","content",new Date(), ContentReviewItem.NOT_SUBMITTED_CODE);
	   contentReviewDao.save(contentReviewItemUnlocked);
	   
	  
	  
   }

   /**
    * ADD unit tests below here, use testMethod as the name of the unit test,
    * Note that if a method is overloaded you should include the arguments in the
    * test name like so: testMethodClassInt (for method(Class, int);
    */


 public void testgetLock() {
	 //Unlocked Item should be able to get lock
	 assertTrue(contentReviewDao.obtainLock(Long.valueOf(contentReviewItemUnlocked.getId()).toString(), ADMIN_USER, 10000));
	 
	 
	 //Item locked by ADMIN I shouldn't be able to get a lock
	 assertFalse(contentReviewDao.obtainLock(Long.valueOf(contentReviewItemLocked.getId()).toString(), USER, 10000));
 
	 //admin should be able to get their origional lock back
	 assertTrue(contentReviewDao.obtainLock(Long.valueOf(contentReviewItemLocked.getId()).toString(), ADMIN_USER, 10000));
	 
	 //not sure why this doesn;t work
	 //assertTrue(contentReviewDao.obtainLock(new Long(contentReviewItemLockedExp.getId()).toString(), USER, 10000));
 }

   /**
    * Add anything that supports the unit tests below here
    */

}
