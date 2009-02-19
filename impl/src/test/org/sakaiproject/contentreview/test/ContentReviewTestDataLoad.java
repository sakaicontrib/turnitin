/**
 * EvalTestDataLoad.java - evaluation - Dec 25, 2006 10:07:31 AM - azeckoski
 * $URL: https://source.sakaiproject.org/contrib/evaluation/trunk/impl/src/test/org/sakaiproject/evaluation/test/EvalTestDataLoad.java $
 * $Id: EvalTestDataLoad.java 46440 2008-03-07 15:21:37Z aaronz@vt.edu $
 **************************************************************************
 * Copyright (c) 2008 Centre for Applied Research in Educational Technologies, University of Cambridge
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 *
 * Aaron Zeckoski (azeckoski@gmail.com) (aaronz@vt.edu) (aaron@caret.cam.ac.uk)
 */

package org.sakaiproject.contentreview.test;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.genericdao.api.GenericDao;


/**
 * This class holds a bunch of items used to prepopulate the database and then
 * do testing, it also handles initilization of the objects and saving
 * (Note for developers - do not modify this without permission from the author)
 * 
 * @author Aaron Zeckoski (aaronz@vt.edu)
 */
public class ContentReviewTestDataLoad {

   // constants
   public final static String USER_NAME = "aaronz";
   public final static String USER_ID = "user-11111111";
   public final static String USER_DISPLAY = "Aaron Zeckoski";
   public final static String ADMIN_USER_ID = "admin";
   public final static String ADMIN_USER_NAME = "admin";
   public final static String ADMIN_USER_DISPLAY = "Administrator";
   public final static String MAINT_USER_ID = "main-22222222";
   public final static String MAINT_USER_NAME = "maintainer";
   public final static String MAINT_USER_DISPLAY = "Maint User";
   public final static String STUDENT_USER_ID = "student-12121212";
   public final static String INVALID_USER_ID = "invalid-XXXX";

   public final static String AUTHZGROUP1A_ID = "authzg-aaaaaaaa";
   public final static String AUTHZGROUP1B_ID = "authzg-bbbbbbbb";
   public final static String AUTHZGROUP2A_ID = "authzg-cccccccc";

   public final static Set<String> AUTHZGROUPSET1 = new HashSet<String>();
   public final static Set<String> AUTHZGROUPSET2 = new HashSet<String>();

   public final static String SITE1_CONTEXT = "siteC1";
   public final static String SITE1_REF = "/sites/ref-111111";
   public final static String SITE1_TITLE = "Site1 title";
   public final static String SITE2_CONTEXT = "siteC2";
   public final static String SITE2_REF = "/sites/ref-222222";
   public final static String SITE2_TITLE = "Site2 title";
   public final static String SITE3_REF = "/sites/ref-333333";

   
   public final static Boolean EXPERT = Boolean.TRUE;
   public final static Boolean NOT_EXPERT = Boolean.FALSE;

   public final static Boolean LOCKED = Boolean.TRUE;
   public final static Boolean UNLOCKED = Boolean.FALSE;

   public final static String ANSWER_TEXT = "text answer";
   public final static Integer ANSWER_SCALED_ONE = Integer.valueOf(1);
   public final static Integer ANSWER_SCALED_TWO = Integer.valueOf(2);
   public final static Integer ANSWER_SCALED_THREE = Integer.valueOf(3);

   public final static String EMAIL_MESSAGE = "This is a big long email message";

   public final static Long INVALID_LONG_ID = Long.valueOf(99999999);
   public final static String INVALID_STRING_EID = "XXXXXXX_XXXXXXXX";
   public final static String INVALID_CONTEXT = "XXXXXXXXXX";
   public final static String INVALID_CONSTANT_STRING = "XXXXXXX_XXXXXXXX";
   public final static int INVALID_CONSTANT_INT = -10;

   public final static Set EMPTY_SET = new HashSet();
   public final static List EMPTY_LIST = new ArrayList();
   public final static Map EMPTY_MAP = new HashMap();
   public final static String[] EMPTY_STRING_ARRAY = new String[0];

   
   // some date objects
   public Date twentyDaysAgo;
   public Date fifteenDaysAgo;
   public Date fourDaysAgo;
   public Date threeDaysAgo;
   public Date yesterday;
   public Date today = new Date();
   public Date tomorrow;
   public Date threeDaysFuture;
   public Date fourDaysFuture;

   /*
    * Some Content Review items
    * 
    */
   public ContentReviewItem item1; 
   
   public ContentReviewItem item2;
   public ContentReviewItem item3;
   public ContentReviewItem item4;
   public ContentReviewItem item5;
   public ContentReviewItem item6;
   

   public ContentReviewTestDataLoad() {
      initialize();

      AUTHZGROUPSET1.add(AUTHZGROUP1A_ID);
      AUTHZGROUPSET1.add(AUTHZGROUP1B_ID);
      AUTHZGROUPSET2.add(AUTHZGROUP2A_ID);
   }

   /**
    * initialize all the objects in this data load pea
    * (this will make sure all the public properties are not null)
    */
   public void initialize() {
	   item1.setContentId("some context id");
	   item1.setDateQueued(today);
	   item1.setSiteId(AUTHZGROUP1A_ID);
	   item1.setStatus(ContentReviewItem.NOT_SUBMITTED_CODE);
	   item1.setTaskId("some task id");
	   item1.setUserId(USER_ID);
	   
   }

   /**
    * Store all of the persistent objects in this pea
    * @param dao A DAO with a save method which takes a persistent object as an argument<br/>
    * Example: dao.save(templateUser);
    */
   public void saveAll(GenericDao dao) {
	   dao.save(item1);
	   dao.save(item2);
	   dao.save(item3);
   }

   /**
    * Take a collection of persistent objects and turn it into a list of the unique ids<br/>
    * Objects in collection must have a Long getId() method<br/>
    * Uses some fun reflection to figure out the IDs
    * 
    * @param c a collection of persistent objects
    * @return a list of IDs (Long)
    */
   public static List<Long> makeIdList(Collection<?> c) {
      List<Long> l = new ArrayList<Long>();
      for (Iterator<?> iter = c.iterator(); iter.hasNext();) {
         Serializable element = (Serializable) iter.next();
         Long id = null;
         try {
            Class<?> elementClass = element.getClass();
            Method getIdMethod = elementClass.getMethod("getId", new Class[] {});
            id = (Long) getIdMethod.invoke(element, (Object[])null);
            l.add(id);
         } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to make id list from collection",e);
         } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to make id list from collection",e);
         } catch (InvocationTargetException e) {
            throw new RuntimeException("Failed to make id list from collection",e);
         }
      }
      return l;
   }

}
