package org.sakaiproject.contentreview.test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.Date;

import junit.framework.TestCase;

import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.sakaiproject.contentreview.impl.turnitin.TurnitinAPIUtil;
import org.springframework.test.AbstractTransactionalSpringContextTests;

public class TurnitinAPITest extends TestCase {

	protected String[] getConfigLocations() {
		return new String[]{};
	}
	
	public void testMyAPI() {
		assertTrue(true);
	}
	
	// 1 Create Instructor
	public void testCreateInstructor() {
		
	}
	
	public void testCreateAssignmentWithNewInstructor() {
		
	}
	
	public void testCreateAssignment() {
		try {
			Date d = new Date();
			//Proxy proxy = new Proxy(Proxy.Type.HTTP, 
			//		new InetSocketAddress(InetAddress.getByAddress(new byte[] {127,0,0,1}), 8008));
			TurnitinAPIUtil.createAssignment(
				"******", // cid
				"******", // ctl 
				"My Test Asnn " + d.getTime(), // assignid
				"My Test Asnn " + d.getTime(), // assignTitle
				"*******", // uem
				"Test First Name", //ufn
				"Test Last Name",  //uln
				"*******", // upw
				"*******", // uid
				"*******", // aid
				"*******", // shared secret
				"*******", //sub account id
				"https://www.turnitin.com/api.asp?", // api url
				null // proxy
			);
		} catch (SubmissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			assertTrue(false);
		} catch (TransientSubmissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			assertTrue(false);
		}
		
	}
	
	// Tests 
	
	// 2 Create Students
	// 3 Create Course
	// 4 Create Assignment
	// 5 Submit some word docs
	// 6 Change the instructor on a course.
	// 7 Test an assignment title that needs to be url encoded
	// 8 Test Updating a class
}

//http://www.turnitin.com/t_class_home.asp?r=33.4183946753424&svr=9&lang=en_us&aid=56021&cid=