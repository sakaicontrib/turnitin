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

class TestTurnitinReviewServiceImpl(unittest.TestCase):

    def setUp(self):
        self.tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")
        self.idmanager = ComponentManager.get("org.sakaiproject.id.api.IdManager")

    # TODO Test Legacy Assignment with createAssignment("asdf","/assignment/adsf")
    # The title should be the Asnn1 title, not the taskid

    def testStudentsViewReports(self):
        """
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

    def testCreateAssignment(self):
        # Test creating a general assignment
        tiiasnnid = "/unittests/"+str(uuid.uuid1())
        self.tiireview_serv.createAssignment("tii-unit-test",tiiasnnid)

        tiiresult = self.tiireview_serv.getAssignment("tii-unit-test", tiiasnnid)
        self.assertEquals(str(tiiresult['object']['assign']),str(tiiasnnid))

    def testCheckAgainstJournals(self):
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

if __name__ == '__main__':
    suite = unittest.TestLoader().loadTestsFromTestCase(TestTurnitinReviewServiceImpl)
    unittest.TextTestRunner(verbosity=2).run(suite)
