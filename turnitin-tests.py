import unittest
import random
from org.sakaiproject.component.cover import ComponentManager
#import uuid
from java.util import HashMap

class SakaiUuid(object):
    """My Current Jython impl doens't seem to have UUID, so re-implementing it 
    for now"""

    def __init__(self):
        self.idmanager = ComponentManager.get("org.sakaiproject.id.api.IdManager")

    def uuid1(self):
        return self.idmanager.createUuid()

uuid = SakaiUuid()

class TestAssignment1Requirements(unittest.TestCase):
    """
    
    """

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

class TestTurnitinReviewServiceImpl(unittest.TestCase):

    def setUp(self):
        self.tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")

    # TODO Test Legacy Assignment with createAssignment("asdf","/assignment/adsf")
    # The title should be the Asnn1 title, not the taskid

    """
    Creating and Reading Turnitin Assignments

    Tests for methods on TurnitinContentReviewServiceImpl that aren't 
    part of the interface, but create and read assignments at Turnitin
    """

    def testCreateAssignment(self):
        """General test to create a basic assignment"""
        tiiasnnid = "/unittests/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment("tii-unit-test",tiiasnnid)

        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
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
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['sviewreports']),str('1'))

        # Test that Students cannot view the report
        opts.put('s_view_report','0')
        tiiasnnid = "/unittests/studcannotviewreport/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['sviewreports']),str('0'))

    def testCheckAgainstStudentRepository(self):
        """
        s_paper_check /  searchpapers 
        values of 0 to not check against student paper  
        repository, 1 to check against it, default is 1
        """
        opts = HashMap()
        tiiasnnid = "/unittests/usestudentrepo/"+str(uuid.uuid1())
        # Test creating an assignment that checks against student repos
        opts.put('s_paper_check','1')
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchpapers']),str('1'))

        # Test creating an assignment that does not check against student repos
        tiiasnnid = "/unittests/nostudentrepo/"+str(uuid.uuid1())
        opts.put('s_paper_check','0')
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
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
        # Test creating an assignment checked against the Internet
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchinternet']),str('1'))

        # Test creating an assignment not checked against the Internet
        opts.put('internet_check','0')
        tiiasnnid = "/unittests/useinternet/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
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
        # Test creating an assignment checked against Journals
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)

        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('1'))

        # Test creating an assignment checked against Journals
        opts.put('journal_check','0')
        tiiasnnid = "/unittests/nojournals/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)
        
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
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
        # Test creating an assignment checked against Journals
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchinstitution']),str('1'))

        # Test creating an assignment checked against Journals
        opts.put('institution_check','0')
        tiiasnnid = "/unittests/noinstitution/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid, opts)
         
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
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
        opts = HashMap()
        opts.put('journal_check','1')
        self.tiireview_serv.createAssignment("tii-unit-test", tiiasnnid, opts)
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('1'))
        
        opts.put('journal_check','0')
        self.tiireview_serv.createAssignment("tii-unit-test", tiiasnnid, opts)
        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['searchjournals']),str('0'))
        
        
tii_testcases = [TestTurnitinSourceSakai, TestTurnitinSourceSakai, TestTurnitinReviewServiceImpl, TestAssignment2Requirements]

def trySomething():
    '''tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
    tiiasnnid = "/unittests/nothere/asdfaasdfsafd"
    tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid)
    tiireview_serv.createAssignment("tii-unit-test",
            tiiasnnid)
    '''
    pass

if __name__ == '__main__':
    tii_suites = []
    for testcase in tii_testcases:
        tii_suites.append(unittest.TestLoader().loadTestsFromTestCase(testcase))
    alltests = unittest.TestSuite(tii_suites)
    unittest.TextTestRunner(verbosity=2).run(alltests)
    trySomething()