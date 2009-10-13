#from test.test_support import verbosity
import unittest
import random
from org.sakaiproject.component.cover import ComponentManager
#import uuid
from java.util import HashMap
from org.sakaiproject.contentreview.exception import SubmissionException
from org.sakaiproject.contentreview.model import ContentReviewItem
from java.lang import Thread
from org.apache.commons.logging import LogFactory

class SakaiUuid(object):
    """My Current Jython impl doens't seem to have UUID, so re-implementing it 
    for now"""

    def __init__(self):
        self.idmanager = ComponentManager.get("org.sakaiproject.id.api.IdManager")

    def uuid1(self):
        return self.idmanager.createUuid()

uuid = SakaiUuid()

test_inst_ids = ['inst01','inst02','inst03']
test_stud_ids = ['stud01','stud02','stud02']

lorum_ipsum = """Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed a lobortis tellus. Etiam blandit pulvinar leo. Ut a dolor ipsum. Aliquam erat volutpat. Suspendisse dapibus neque eget erat pellentesque malesuada. In et augue leo. Vivamus mattis accumsan urna, eget congue elit ullamcorper sed. Pellentesque sed magna hendrerit purus dapibus bibendum sit amet a leo. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Donec sed erat massa, eget tristique ante. Donec iaculis fermentum est at luctus. Aliquam vitae mi a sem vestibulum egestas sed eget augue. Ut feugiat sodales sem non molestie. Quisque quam tellus, accumsan eget congue sed, egestas ac risus. Quisque sed neque non nisl ultricies tristique. Proin auctor accumsan nisl nec laoreet. Suspendisse semper erat elit, id malesuada libero. Pellentesque consequat, elit in aliquet volutpat, enim lacus pulvinar massa, vel placerat tellus urna id purus. Suspendisse potenti.

Morbi adipiscing porttitor auctor. Fusce vitae velit metus, ut scelerisque mauris. Integer et mi arcu. Nulla facilisi. Cras vel nisi sem, ut sagittis tortor. Praesent dapibus egestas nibh vel ullamcorper. Mauris non nisi et est varius tristique. Maecenas gravida, nibh et euismod lobortis, justo dui ullamcorper nulla, vel euismod enim arcu sed tortor. Cras tincidunt turpis ligula. Phasellus aliquet luctus massa, id vehicula nisl consequat nec. Ut pharetra libero at velit faucibus eu rhoncus mi imperdiet. Morbi malesuada adipiscing vestibulum. Aliquam eu neque nibh, a tempor diam. In vitae nunc elit. Nunc a nisi mi, tempor porttitor ipsum. Nunc sem augue, luctus nec fermentum vitae, tempor ac velit. Fusce tincidunt congue hendrerit. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Suspendisse eu lectus vitae urna egestas condimentum sed vitae enim. Suspendisse id magna non elit porta sodales.

Quisque tristique interdum adipiscing. Aliquam erat volutpat. Nam commodo sodales volutpat. Donec vestibulum faucibus augue eu posuere. Aenean nibh justo, eleifend eget blandit in, varius et risus. Nullam at metus laoreet leo dapibus imperdiet a eu mi. Quisque mollis convallis convallis. Maecenas mattis ullamcorper ultrices. Donec pretium facilisis lacus, eu rhoncus massa interdum quis. Praesent in eros turpis, nec commodo nisi. Nullam adipiscing dapibus nisi ut elementum. Vestibulum a lectus justo, vitae tristique risus.

Nullam tristique, nisi vitae vestibulum lobortis, odio mauris consequat urna, a lacinia arcu diam feugiat augue. Nullam ut pretium ipsum. Maecenas posuere tincidunt massa et rutrum. Etiam suscipit, est nec lacinia ullamcorper, sapien leo porta leo, et auctor ligula sapien vel erat. Maecenas facilisis tortor ac ligula placerat id blandit nibh fermentum. Aenean vel turpis diam. Morbi non elit eget nisi sagittis molestie. Cras semper libero sit amet odio molestie rutrum. Vestibulum congue convallis urna, eu gravida felis ornare nec. Fusce luctus tincidunt leo, tempor commodo risus tincidunt vel. Vivamus viverra tempus lacinia. Proin porta viverra nunc, ornare sollicitudin sem blandit non. Proin scelerisque, lectus et bibendum laoreet, est ipsum sollicitudin nunc, eu ornare quam purus molestie augue. Praesent eleifend euismod nulla, a interdum nunc congue a. Duis quis lectus dolor. Proin ultricies purus id arcu pharetra luctus. Nam egestas urna nec ipsum tincidunt molestie.

Nulla eu tortor erat. Morbi adipiscing metus quis mauris tristique id lobortis metus vestibulum. Ut ultrices molestie lacus, nec pellentesque diam porta vitae. Morbi interdum neque et nisi porttitor sodales ac id ipsum. Quisque sit amet sapien risus. Curabitur gravida dui non magna vehicula mattis. Aenean venenatis faucibus volutpat. Donec vitae est sapien, in feugiat metus. Praesent eget libero sapien, a gravida lacus. Curabitur neque orci, vehicula ac mattis a, sodales nec risus. In lectus magna, cursus eget dapibus malesuada, scelerisque sed justo. Vivamus dignissim magna vitae risus sollicitudin mollis. Aliquam adipiscing, est sit amet euismod vestibulum, erat nisi auctor justo, id ultricies odio libero ut nisl. Praesent aliquet, eros eu eleifend vestibulum, erat urna ultricies leo, sed lacinia justo sapien mollis sem. Suspendisse et nunc in dui molestie blandit a sed augue. Pellentesque libero purus, dapibus eu rhoncus eget, bibendum vitae leo. In nibh neque, vulputate a venenatis ut, sagittis ut justo. Vestibulum enim neque, bibendum quis varius sed, dictum sit amet quam. 
"""

def addExampleAttachment(content=None):
    """Returns a tuple of the Resource ID, and then potentially the actual
    ContentResource object"""
    content_srv = ComponentManager.get("org.sakaiproject.content.api.ContentHostingService")
    if content == None:
        payload = lorum_ipsum
    else:
        payload = content
    cres = content_srv.addAttachmentResource("tiitest","tiiintegrationtest",payload, None)
    return cres.id, cres

def createTestUsers():
    """
    This will create some users we can use for the tests.
    """
    user_serv = ComponentManager.get("org.sakaiproject.user.api.UserDirectoryService")
    
    users= []
    users.extend(test_inst_ids)
    users.extend(test_stud_ids)
    
    for i in users:
        try:
            user_serv.addUser(None,i,"First"+i,"Last"+i,i+"@sakaitest.org","tester",None,None)
        except:
            pass
    
def becomeUser(userEid="inst01"):
    """
    Some methods are keyed to depend on the current thread bound user. This will
    change the current thread to the userid passed in.
    
    If userid is None this will effectively clear the current session to no user
    is logged in.
    """
    session_mgr = ComponentManager.get("org.sakaiproject.tool.api.SessionManager")
    authz_srv = ComponentManager.get("org.sakaiproject.authz.api.AuthzGroupService")
    security_srv = ComponentManager.get("org.sakaiproject.authz.api.SecurityService")
    user_serv = ComponentManager.get("org.sakaiproject.user.api.UserDirectoryService")
    
    session = session_mgr.getCurrentSession()
    user = user_serv.getUserByEid(userEid)
    session.clear()
    session.setUserId(user.getId())
    session.setUserEid(user.getEid())
    authz_srv.refreshUser(user.getId())

class TestAssignment1Requirements(unittest.TestCase):
    
    def setUp(self):
        pass
    

class TestAssignment2Requirements(unittest.TestCase):
    """
    This set of tests will look at making sure the parameters and options being
    used by Assignments 2 continue to work.
    """
    def setUp(self):
        self.tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
    
    def testCreateAsnn2(self):
        "According to the TII API assignments only have to be unique in a class"
        siteid = str(uuid.uuid1())
        asnnid = "/assignment2/1"
        self.tiireview_serv.createAssignment(siteid,asnnid)
        print(siteid)
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['assign']),str(asnnid))

class TestTurnitinSourceSakai(unittest.TestCase):
    """
    This set of tests will test the ability to use the src=9 parameter to
    indicate that this is a Sakai installation and we want the psuedo 
    provisioning. This must be turned on by Turnitin for the specific 
    enterprise account using it.
    """
    def setUp(self):
        self.tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")

    def testExample(self):
        pass 


class BaseTurnitinReviewServiceImpl(unittest.TestCase):
    """
    Some lessons learned.  We have to use unique siteId's for testing. This is
    because it's not transparent to convert a src9 site back and forth, so we
    have to use unique sites/classes/cids/ctls when doing testing since these
    tests are meant to be reused for both src9 and defaultInstructor
    testing.
    """

    def setUp(self):
        self.tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
        self.cachedSourceParameter = self.tiireview_serv.useSourceParameter
        self.tiireview_serv.useSourceParameter = self.useSourceParameter
        self.log = LogFactory.getLog("TestTurnitinReviewServiceImpl")
        self.user_serv = ComponentManager.get("org.sakaiproject.user.api.UserDirectoryService")
        
    def tearDown(self):
        self.tiireview_serv.useSourceParameter = self.cachedSourceParameter
        
        
    # TODO Test Legacy Assignment with createAssignment("asdf","/assignment/adsf")
    # The title should be the Asnn1 title, not the taskid

    def testCreateClass(self):
        """Integration test for TurnitinReviewServiceImpl.createClass
        method."""
        # Does failUnlessRaises not work in Jython 2.2?
        #self.failUnlessRaises(SubmissionException, self.tiireview_serv.createClass, "1")
        #self.tiireview_serv.createClass("1")
        tiiclassid = str(uuid.uuid1())
        self.tiireview_serv.createClass(tiiclassid)

    def testEnrollInClass(self):
        """Integration Test for TurnitinReviewServiceImpl.enrollInClass
        TODO Change source code to make this test have better results to 
        verify against (ie. return the rMessage and rCode for instance)
        """
        user_serv = ComponentManager.get("org.sakaiproject.user.api.UserDirectoryService")
        tiiclassid = str(uuid.uuid1())
        tiiasnnid = str(uuid.uuid1())
        tiiemail = str(uuid.uuid1()) + "@sakaiproject.org"
        # SWG TODO Why does this fail if you create the Class first?
        self.tiireview_serv.createClass(tiiclassid)
        Thread.sleep(1000)
        self.tiireview_serv.createAssignment(tiiclassid, tiiasnnid )
        Thread.sleep(1000)
        self.tiireview_serv.enrollInClass(user_serv.getUserId("stud01"), 
                                        tiiemail, tiiclassid)

    def testQueueContent(self):
        """Integration test for ContentReviewService.queueContent"""
        user_serv = ComponentManager.get("org.sakaiproject.user.api.UserDirectoryService")
        tiiclassid = str(uuid.uuid1())
        tiiasnnid = str(uuid.uuid1())
        tiiemail = str(uuid.uuid1()) + "@sakaiproject.org"
        userid = user_serv.getUserId("stud01")
        tiicontentid = addExampleAttachment()[0]
        #
        #self.tiireview_serv.createClass(tiiclassid)
        self.tiireview_serv.createAssignment(tiiclassid, tiiasnnid )
        Thread.sleep(1000)
        self.tiireview_serv.enrollInClass(userid, 
                                        tiiemail, tiiclassid)
        Thread.sleep(1000)
        self.tiireview_serv.queueContent(userid, tiiclassid, tiiasnnid, tiicontentid)
        #TODO Do the same thing the quartz job would
        self.tiireview_serv.processQueue()
        self.tiireview_serv.checkForReports()
        status = self.tiireview_serv.getReviewStatus(tiicontentid)
        self.log.warn("The status is: " + str(status))
        self.assert_(status in [ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE, ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE])
        self.assertNotEqual(status, ContentReviewItem.NOT_SUBMITTED_CODE)
        
    def enrollAndQueueContentForStudent(self, usereid, tiiclassid, tiiasnnid, tiiemail=None):
        """Enrolls a user in a class and submits an assignment on their behalf. 
        Convenience method to ramp up data for processing and bulk tests.
        Returns the Content Id for the faux assignment they submitted."""
        if tiiemail == None:
            tiiemail = str(uuid.uuid1()) + "@sakaiproject.org"
        userid = self.user_serv.getUserId("stud01")
        tiicontentid = addExampleAttachment()[0]
        self.tiireview_serv.enrollInClass(userid, tiiemail, tiiclassid)
        self.tiireview_serv.queueContent(userid, tiiclassid, tiiasnnid, tiicontentid)
        return tiicontentid
        
    def testCheckForReportsBulk(self):
        """Test for TurnitinReviewServiceImpl.checkForReportsBulk"""
        tiiclassid = str(uuid.uuid1())
        tiiasnnid = str(uuid.uuid1())
        self.tiireview_serv.createClass(tiiclassid)
        Thread.sleep(1000)
        self.tiireview_serv.createAssignment(tiiclassid, tiiasnnid )
        
        tiicontentid = self.enrollAndQueueContentForStudent("stud01", tiiclassid, tiiasnnid)
        tiicontentid2 = self.enrollAndQueueContentForStudent("stud02", tiiclassid, tiiasnnid)
        
        #TODO Do the same thing the quartz job would
        self.tiireview_serv.processQueue()
        self.tiireview_serv.checkForReportsBulk()
        for contentid in [tiicontentid, tiicontentid2]:
            status = self.tiireview_serv.getReviewStatus(contentid)
            self.log.warn("The status for " + contentid + " is: " + str(status))
            self.assert_(status in [ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE, ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE])
            self.assertNotEqual(status, ContentReviewItem.NOT_SUBMITTED_CODE)
            
    def testCheckForReportsIndividual(self):
        """Test for TurnitinReviewServiceImpl.checkForReportsIndividual"""
        tiiclassid = str(uuid.uuid1())
        tiiasnnid = str(uuid.uuid1())
        self.tiireview_serv.createClass(tiiclassid)
        Thread.sleep(1000)
        self.tiireview_serv.createAssignment(tiiclassid, tiiasnnid )
        
        tiicontentid = self.enrollAndQueueContentForStudent("stud01", tiiclassid, tiiasnnid)
        tiicontentid2 = self.enrollAndQueueContentForStudent("stud02", tiiclassid, tiiasnnid)
        
        #TODO Do the same thing the quartz job would
        self.tiireview_serv.processQueue()
        self.tiireview_serv.checkForReportsIndividual()
        for contentid in [tiicontentid, tiicontentid2]:
            status = self.tiireview_serv.getReviewStatus(contentid)
            self.log.warn("The status for " + contentid + " is: " + str(status))
            self.assert_(status in [ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE, ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE])
            self.assertNotEqual(status, ContentReviewItem.NOT_SUBMITTED_CODE)

    def testGetReviewReportInstructor(self):
        """Test URLS from ContentReviewService.getReviewReportInstructor"""
        """tiiclassid = str(uuid.uuid1())
        tiiasnnid = str(uuid.uuid1())
        self.tiireview_serv.createClass(tiiclassid)
        Thread.sleep(1000)
        self.tiireview_serv.createAssignment(tiiclassid, tiiasnnid )
        
        tiicontentid = self.enrollAndQueueContentForStudent("stud01", tiiclassid, tiiasnnid)
        tiicontentid2 = self.enrollAndQueueContentForStudent("stud02", tiiclassid, tiiasnnid)
        
        #TODO Do the same thing the quartz job would
        self.tiireview_serv.processQueue()
        self.tiireview_serv.checkForReports()
        for contentid in [tiicontentid, tiicontentid2]:
            reviewURL = self.tiireview_serv.getReviewReportInstructor(contentid)
            self.log.warn("THE REVIEW URL: " + reviewURL)
        #    status = self.tiireview_serv.getReviewStatus(contentid)
        #    #self.log.warn("The status for " + contentid + " is: " + str(status))
        #    self.assert_(status in [ContentReviewItem.SUBMITTED_REPORT_AVAILABLE_CODE, ContentReviewItem.SUBMITTED_AWAITING_REPORT_CODE])
        #    self.assertNotEqual(status, ContentReviewItem.NOT_SUBMITTED_CODE)
        """
        pass

    """
    Creating and Reading Turnitin Assignments

    Tests for methods on TurnitinContentReviewServiceImpl that aren't 
    part of the interface, but create and read assignments at Turnitin
    """

    def testCreateAssignment(self):
        """General test to create a basic assignment"""
        tiiasnnid = "/unittests/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        self.tiireview_serv.createAssignment(tiisiteid,tiiasnnid)

        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['assign']),str(tiiasnnid))

    """
    Repositories to check originality against, there are 4 at the moment. Student,
    Internet, Journals, and Institution
    """

    def testStudentsViewReports(self):
        """ Option deciding whether students can view the originality report.
        s_view_report / sviewreports
        0 = not allowed
        1 = allowed
        default is 0
        """

        # First Test that Students can view the report
        opts = HashMap()
        opts.put('s_view_report','1')
        tiiasnnid = "/unittests/studcanviewreport/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['sviewreports']),str('1'))

        # Test that Students cannot view the report
        opts.put('s_view_report','0')
        tiiasnnid = "/unittests/studcannotviewreport/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['sviewreports']),str('0'))

    def testCheckAgainstStudentRepository(self):
        """
        s_paper_check /  searchpapers 
        values of 0 to not check against student paper  
        repository, 1 to check against it, default is 1
        """
        opts = HashMap()
        tiiasnnid = "/unittests/usestudentrepo/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        # Test creating an assignment that checks against student repos
        opts.put('s_paper_check','1')
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchpapers']),str('1'))

        # Test creating an assignment that does not check against student repos
        tiiasnnid = "/unittests/nostudentrepo/"+str(uuid.uuid1())
        opts.put('s_paper_check','0')
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchpapers']),str('0'))

    def testCheckAgainstInternetRepository(self):
        """
        internet_check / searchinternet
        values of 0 to not check against internet, 1 to  
        check against it, default is 1
        """
        opts = HashMap()
        opts.put('internet_check','1')
        tiiasnnid = "/unittests/useinternet/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        # Test creating an assignment checked against the Internet
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchinternet']),str('1'))

        # Test creating an assignment not checked against the Internet
        opts.put('internet_check','0')
        tiiasnnid = "/unittests/useinternet/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchinternet']),str('0'))

    def testCheckAgainstJournalsRepository(self):
        """
        journal_check / searchjournals 
        values of 0 to not check against periodicals, etc.,  
        1 to check against it, default is 1"
        """
        opts = HashMap()
        opts.put('journal_check','1')
        tiiasnnid = "/unittests/usejournals/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        # Test creating an assignment checked against Journals
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('1'))

        # Test creating an assignment checked against Journals
        opts.put('journal_check','0')
        tiiasnnid = "/unittests/nojournals/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('0'))

    def testCheckAgainstInstitutionRepository(self):
        """
        institution_check / searchinstitution 
        values of 0 to not check against institutional
        repository, 1 check against it.
        """
        opts = HashMap()
        opts.put('institution_check','1')
        tiiasnnid = "/unittests/useinstitution/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        # Test creating an assignment checked against Journals
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchinstitution']),str('1'))

        # Test creating an assignment checked against Journals
        opts.put('institution_check','0')
        tiiasnnid = "/unittests/noinstitution/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
         
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchinstitution']),str('0'))
        
    def testGetNonExistantAssignment(self):
        """Trying to fetch an assignment that does not exist should return
        error code 206
        """
        tiiasnnid = "/unittests/nothere/"+str(uuid.uuid1())
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals("206", str(tiiresult["rcode"]))
        
    def testUpdatingExistingAssignment(self):
        """Test to make sure changes to an existing assignment are getting
        updated and saved.
        """
        tiiasnnid = "/unittests/asnnupdate/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        opts = HashMap()
        opts.put('journal_check','1')
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        Thread.sleep(1000)
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        Thread.sleep(1000)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('1'))
        Thread.sleep(1000)
        opts.put('journal_check','0')
        
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        Thread.sleep(1000)
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('0'))
        
    def testUpdateAssignment(self):
        """TurnitinReviewServiceImpl.updateAssignment
        
        TODO: I think this is suppose to actually test updating the due date.
        I believe the occasional running of doAssignments is to get around the
        5 month due date limitation."""
        tiiasnnid = "/unittests/asnnupdate/"+str(uuid.uuid1())
        tiisiteid = str(uuid.uuid1())
        opts = HashMap()
        opts.put('journal_check','1')
        self.tiireview_serv.createAssignment(tiisiteid, tiiasnnid, opts)
        Thread.sleep(1000)
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        Thread.sleep(1000)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('1'))
        Thread.sleep(1000)
        
        self.tiireview_serv.updateAssignment(tiisiteid, tiiasnnid)
        Thread.sleep(1000)
        tiiresult = self.tiireview_serv.getAssignment(tiisiteid, tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('1'))
        
        
    """The following tests below are for ONC-1834: ie. Setting the Due Date on 
    the Turnitin assignment. We need to test a number of boundary cases. Also,
    from the comments in the code it looks like you can only set the due date
    5 months out?
    """
    #def testAsnnDueDate(self):
    #    self.assertFalse(True)

class Src9TurnitinReviewServiceImpl(BaseTurnitinReviewServiceImpl):
    useSourceParameter = True

class DefaultInstructorTurnitinReviewServiceImpl(BaseTurnitinReviewServiceImpl):
    useSourceParameter = False

#tii_testcases = [TestTurnitinSourceSakai, TestTurnitinSourceSakai, TestTurnitinReviewServiceImpl, TestAssignment2Requirements]
tii_testcases = [Src9TurnitinReviewServiceImpl]     #DefaultInstructorTurnitinReviewServiceImpl] #, Src9TurnitinReviewServiceImpl]

def trySomething(*args):
    '''tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
    tiiasnnid = "/unittests/nothere/asdfaasdfsafd"
    tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid)
    tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid)
    '''
    content_id = "/attachment/27eb8fe5-10c0-4f70-9b8c-8031e0e6b851/Assignment2/fc263898-89ef-496c-b8ec-21e4d8c0e0f2/A2IT3_V4.pdf"
    tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
    #tiireview_serv.checkForReports()
    reviewURL = tiireview_serv.getReviewReportInstructor(content_id)
    print("Instructor URL")
    print(reviewURL)
    print("Student URL")
    studentReviewURL = tiireview_serv.getReviewReportStudent(content_id)
    print(studentReviewURL)
    
    

def runTests():
    becomeUser("inst03")
    tii_suites = []
    for testcase in tii_testcases:
        tii_suites.append(unittest.TestLoader().loadTestsFromTestCase(testcase))
    alltests = unittest.TestSuite(tii_suites)
    unittest.TextTestRunner(verbosity=5).run(alltests)
    becomeUser("admin")

def runTest(testname):
    becomeUser("inst03")
    test_suite = unittest.TestSuite()
    test_suite.addTest(Src9TurnitinReviewServiceImpl(testname))
    unittest.TextTestRunner().run(test_suite)
    becomeUser("admin")
    
def showTests():
    for testcase in tii_testcases:
        print(testcase)
        for attr in dir(testcase):
            if attr.startswith("test"):
                print("  %s" % (attr))

def debugTests():
    becomeUser("inst03")
    print("Trying to run a test with debug")
    #test = TestTurnitinReviewServiceImpl()
    #test.run()
    tii_suites = []
    for testcase in tii_testcases:
        tii_suites.append(unittest.TestLoader().loadTestsFromTestCase(testcase))
    alltests = unittest.TestSuite(tii_suites)
    alltests.debug()
    #unittest.TextTestRunner(verbosity=5).run(alltests)
    becomeUser("admin")

def usage():
    """Returns usage string. May be different on what's installed in this Sakai 
    Instances"""
    return '''
turnitin runtests
   - runs tests
   
turnitin runtest testname
   - run a single named test

turnitin showtests
   - show all available tests

turnitin viewasnn siteid taskid
   - view the information for an assignment with taskid in the site
   
turnitin processqueue
turnitin checkforreports
'''

def viewAssignment(siteid,taskid):
    "Spits out the returned XML from the Turnitin View Asnn API Call"
    tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
    return tiireview_serv.getAssignment(siteid,taskid)

def processQueue():
    tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
    tiireview_serv.processQueue()

def checkForReports():
    tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
    tiireview_serv.checkForReports()

def main(args):
    if len(args) > 0 and args[0] == "runtests":
        runTests()
    elif len(args) > 0 and args[0] == "showtests":
        showTests()
    elif len(args) > 0 and args[0] == "processqueue":
        processQueue()
    elif len(args) > 0 and args[0] == "checkforreports":
        checkForReports()
    elif len(args) > 1 and args[0] == "runtest":
        runTest(args[1])
    elif len(args) > 0 and args[0] == "debugtests":
        debugTests()
    elif len(args) >= 3 and args[0] == "viewasnn":
        print(viewAssignment(args[1], args[2]))
    elif len(args) > 0 and args[0] == "proto":
        trySomething(*args[1:])
    else:
        print(usage())

if __name__ == '__main__':
    main(sys.argv[1:])
