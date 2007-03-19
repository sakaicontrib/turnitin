/**********************************************************************************
* $URL: https://source.sakaiproject.org/svn/jobscheduler/branches/sakai_2-3-x/scheduler-component-shared/src/java/org/sakaiproject/component/app/scheduler/jobs/TestBeanJob.java $
* $Id: TestBeanJob.java 4396 2005-12-02 00:58:02Z john.ellis@rsmart.com $
***********************************************************************************
*
* Copyright (c) 2003, 2004 The Regents of the University of Michigan, Trustees of Indiana University,
*                  Board of Trustees of the Leland Stanford, Jr., University, and The MIT Corporation
*
* Licensed under the Educational Community License Version 1.0 (the "License");
* By obtaining, using and/or copying this Original Work, you agree that you have read,
* understand, and will comply with the terms and conditions of the Educational Community License.
* You may obtain a copy of the License at:
*
*      http://cvs.sakaiproject.org/licenses/license_1_0.html
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
* INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
* AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
* DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*
**********************************************************************************/
package org.sakaiproject.component.app.scheduler.jobs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.contentreview.service.ContentReviewService;


public class ContentReviewJob implements Job {

   public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
      contentReviewService.processQueue();
      contentReviewService.checkForReports();
   }

   private ContentReviewService contentReviewService;
   public void setContentReviewService (ContentReviewService contentReviewService) {
	   this.contentReviewService = contentReviewService;
   }
}
