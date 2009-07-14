package org.sakaiproject.contentreview.test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.Date;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.sakaiproject.contentreview.impl.turnitin.TurnitinAPIUtil;
import org.sakaiproject.contentreview.impl.turnitin.TurnitinUtil;
import org.springframework.test.AbstractTransactionalSpringContextTests;
import org.w3c.dom.Document;

import com.thoughtworks.xstream.XStream;

public class TurnitinAPITest extends TestCase {
    private static final Log log = LogFactory.getLog(TurnitinAPITest.class);

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
    
    // Extra fid4 options
    private static String SUBMIT_PAPERS_TO = "submit_papers_to";
    private static String REPORT_GEN_SPEED = "report_gen_speed";
    private static String S_VIEW_REPORT = "s_view_report";
    
    private void standardCreateAssignment(String assignid, String assigntitle, String ... vargs) {
        try {
            //Proxy proxy = new Proxy(Proxy.Type.HTTP, 
            //              new InetSocketAddress(InetAddress.getByAddress(new byte[] {127,0,0,1}), 8008));
            TurnitinAPIUtil.createAssignment(
                    TurnitinUtil.TEST_CID, // cid
                    TurnitinUtil.TEST_CTL, // ctl 
                    assignid, // assignid
                    assigntitle, // assignTitle
                    TurnitinUtil.TEST_UEM, // uem
                    TurnitinUtil.TEST_UFN, //ufn
                    TurnitinUtil.TEST_ULN,  //uln
                    TurnitinUtil.TEST_UPW, // upw
                    TurnitinUtil.TEST_UID, // uid
                    TurnitinUtil.TEST_AID, // aid
                    TurnitinUtil.TEST_SHARED_SECRET, // shared secret
                    TurnitinUtil.TEST_AID, //sub account id
                    TurnitinUtil.TEST_APIURL, // api url
                    TurnitinUtil.TEST_PROXY, // proxy
                    vargs
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
    
    private void fetchStandardAssignment(String assignid, String assigntitle, String ... vargs) {
        try {
             TurnitinAPIUtil.getAssignment(
            		TurnitinUtil.TEST_CID, // cid
            		TurnitinUtil.TEST_CTL, // ctl 
                    assignid, // assignid
                    assigntitle, // assignTitle
                    TurnitinUtil.TEST_UEM, // uem
                    TurnitinUtil.TEST_UFN, //ufn
                    TurnitinUtil.TEST_ULN,  //uln
                    TurnitinUtil.TEST_UPW, // upw
                    TurnitinUtil.TEST_UID, // uid
                    TurnitinUtil.TEST_AID, // aid
                    TurnitinUtil.TEST_SHARED_SECRET, // shared secret
                    TurnitinUtil.TEST_AID, //sub account id
                    TurnitinUtil.TEST_APIURL, // api url
                    TurnitinUtil.TEST_PROXY, // proxy
                    vargs
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
    
    public void testNonExistingAssignmentFetch() {
        try {
            TurnitinAPIUtil.getAssignment(
            		TurnitinUtil.TEST_CID, // cid
            		TurnitinUtil.TEST_CTL, // ctl 
                   "notanassignment132425", // assignid
                   "notanassignment132425", // assignTitle
                   TurnitinUtil.TEST_UEM, // uem
                   TurnitinUtil.TEST_UFN, //ufn
                   TurnitinUtil.TEST_ULN,  //uln
                   TurnitinUtil.TEST_UPW, // upw
                   TurnitinUtil.TEST_UID, // uid
                   TurnitinUtil.TEST_AID, // aid
                   TurnitinUtil.TEST_SHARED_SECRET, // shared secret
                   TurnitinUtil.TEST_AID, //sub account id
                   TurnitinUtil.TEST_APIURL, // api url
                   TurnitinUtil.TEST_PROXY // proxy
                   
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
    
    public void testCreateClass() {
        Date d = new Date();
        
        String classname = "My Test Class " + d.getTime();
        try {
            TurnitinAPIUtil.createClass(
                    classname, // cid
                    classname, //ctl, 
                    "sdlkfgjdw", //cpw, 
                    TurnitinUtil.TEST_UEM, // uem, 
                    TurnitinUtil.TEST_UFN, // ufn, 
                    TurnitinUtil.TEST_ULN, // uln,
                    TurnitinUtil.TEST_UPW, // upw, 
                    TurnitinUtil.TEST_UID, // uid, 
                    TurnitinUtil.TEST_AID, // aid, 
                    TurnitinUtil.TEST_SHARED_SECRET, // secretKey, 
                    TurnitinUtil.TEST_AID, // said, 
                    TurnitinUtil.TEST_APIURL, // apiURL, 
                    TurnitinUtil.TEST_PROXY // proxy
                    );
            
            TurnitinAPIUtil.createAssignment(
                    classname, // cid
                    classname, // ctl 
                    "FirstAssign" + d.getTime(), // assignid
                    "FirstAssign" + d.getTime(), // assignTitle
                    TurnitinUtil.TEST_UEM, // uem
                    TurnitinUtil.TEST_UFN, //ufn
                    TurnitinUtil.TEST_ULN,  //uln
                    TurnitinUtil.TEST_UPW, // upw
                    TurnitinUtil.TEST_UID, // uid
                    TurnitinUtil.TEST_AID, // aid
                    TurnitinUtil.TEST_SHARED_SECRET, // shared secret
                    TurnitinUtil.TEST_AID, //sub account id
                    TurnitinUtil.TEST_APIURL, // api url
                    TurnitinUtil.TEST_PROXY // proxy
                    
            );
        } catch (SubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            assertTrue(false);
        } catch (TransientSubmissionException e) {
            // TODO Auto-generated catch block
            assertTrue(false);
        }
        
    }

    public void testCreateAssignment() {
        Date d = new Date();
        standardCreateAssignment("My Test Asnn " + d.getTime(), "My Test Asnn " + d.getTime());
    }
    
    /**
     * submit_papers_to
     * 0 = do not submit to repository
     * 1 = submit to standard repository
     * 2 = submit to institutional repository
     */
    public void testRepositoryToSubmitTo() {
        
        
        Date d = new Date();
        String idtitle = "Do Not Submit to Repo: " + d.getTime();
        standardCreateAssignment(idtitle, idtitle, SUBMIT_PAPERS_TO, "0");
        
        idtitle = "Submit to Standard Repo: " + d.getTime();
        standardCreateAssignment(idtitle, idtitle, SUBMIT_PAPERS_TO, "1");
        
        idtitle = "Submit to Inst Repo: " + d.getTime();
        standardCreateAssignment(idtitle, idtitle, SUBMIT_PAPERS_TO, "2");
        
        idtitle = "Default Repo Option: " + d.getTime();
        standardCreateAssignment(idtitle, idtitle);
        
        idtitle = "Blank Repo Option: " + d.getTime();
        standardCreateAssignment(idtitle, idtitle, SUBMIT_PAPERS_TO, "");
        
        fetchStandardAssignment(idtitle, idtitle);
        
        log.debug("Fetching Assignment for RepoToSubmitTo Test:");
       // log.debug(document);
        
       
    }
    
    /**
     * report_gen_speed
     * 0 = first report is final
     * 1 = can override until due date
     * 2 = on due date
     * 
     * default is 0
     */
    public void testWhenToGenerateOrigReports() {
        
        
        Date d = new Date();
        String idtitle = "ReportGen: First report is final " + d.getTime();
        standardCreateAssignment(idtitle, idtitle, REPORT_GEN_SPEED, "0");
        
        idtitle = "ReportGen: can override until due " + d.getTime();
        standardCreateAssignment(idtitle, idtitle, REPORT_GEN_SPEED, "1");
        
        idtitle = "ReportGen: on due date " + d.getTime();
        standardCreateAssignment(idtitle, idtitle, REPORT_GEN_SPEED, "2");

        idtitle = "ReportGen: Default (first report is final) " + d.getTime();
        standardCreateAssignment(idtitle, idtitle);
    }
    
    /**
     * s_view_report
     * 0 = not allowed
     * 1 = allowed
     * 
     * default is 0
     * 
     */
    public void testAllowStudentsToSeeOriginalityReports() {
       
       
       Date d = new Date();
       String idtitle = "Student CANNOT See Orig Report " + d.getTime();
       standardCreateAssignment(idtitle, idtitle, S_VIEW_REPORT, "0");
       
       idtitle = "Student CAN See Orig Report " + d.getTime();
       standardCreateAssignment(idtitle, idtitle, S_VIEW_REPORT, "1");
         
       // NOte: this is actually hardcoded in the existing method
       //idtitle = "Default: Student CANNOT See Orig Report " + d.getTime();
       //standardCreateAssignment(idtitle, idtitle); 
    }
    
    /**
     *  
     */
    public void testCheckOriginalityAgainstStudentPaperRepo() {
        
    }

    //public void test

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