"""
Test script for src=9 provisioning

Below are some odd examples and notes:
Adding a class
{
  'src': '9', 
  'uln': 'Githens', 
  'ufn': 'Steven', 
  'aid': '56021', 
  'utp': '2', 
  'said': '56021', 
  'fid': '2', 
  'username': 'swgithen', 
  'ctl': 'CourseTitleb018b622-b425-4af7-bb3d-d0d2b4deb35c', 
  'diagnostic': '0', 
  'encrypt': '0', 
  'uem': 'swgithen@mtu.edu', 
  'cid': 'CourseTitleb018b622-b425-4af7-bb3d-d0d2b4deb35c', 
  'fcmd': '2'
}
{rmessage=Successful!, userid=17463901, classid=2836785, rcode=21}
Adding an assignment
{  
  'fid': '4', 
  'diagnostic': '0', 
  'ufn': 'Steven', 
  'uln': 'Githens', 
  'username': 'swgithen', 
  'assignid': 'AssignmentTitlec717957d-254f-4d6d-a64c-952e630db872', 
  'aid': '56021', 
  'src': '9', 
  'cid': 'CourseTitleb018b622-b425-4af7-bb3d-d0d2b4deb35c', 'said': '56021', 'dtstart': '20091225', 'encrypt': '0', 'assign': 'AssignmentTitlec717957d-254f-4d6d-a64c-952e630db872', 'uem': 'swgithen@mtu.edu', 'utp': '2', 'fcmd': '2', 'ctl': 'CourseTitleb018b622-b425-4af7-bb3d-d0d2b4deb35c', 'dtdue': '20100101'}
{rmessage=Successful!, userid=17463901, classid=2836785, assignmentid=7902977, rcode=41}
Adding an assignment with another inst
{'fid': '4', 'diagnostic': '0', 'ufn': 'StevenIU', 'uln': 'GithensIU', 'username': 'sgithens', 'assignid': 'AssignmentTitle5ae51e10-fd60-4720-931b-ed4f58057d00', 'aid': '56021', 'src': '9', 'cid': '2836785', 'said': '56021', 'dtstart': '20091225', 'encrypt': '0', 'assign': 'AssignmentTitle5ae51e10-fd60-4720-931b-ed4f58057d00', 'uem': 'sgithens@iupui.edu', 'utp': '2', 'fcmd': '2', 'ctl': 'CourseTitleb018b622-b425-4af7-bb3d-d0d2b4deb35c', 'dtdue': '20100101'}
{rmessage=Successful!, userid=17463902, classid=2836786, assignmentid=7902978, rcode=41}

Adding a class
{'src': '9', 'uln': 'Githens', 'ufn': 'Steven', 'aid': '56021', 'utp': '2', 'said': '56021', 'fid': '2', 'username': 'swgithen', 'ctl': 'CourseTitle46abd163-7464-4d21-a2c0-90c5af3312ab', 'diagnostic': '0', 'encrypt': '0', 'uem': 'swgithen@mtu.edu', 'fcmd': '2'}
{rmessage=Successful!, userid=17259618, classid=2836733, rcode=21}
Adding an assignment
{'fid': '4', 'diagnostic': '0', 'ufn': 'Steven', 'uln': 'Githens', 'username': 'swgithen', 'assignid': 'AssignmentTitlec4f211c1-2c38-4daf-86dc-3c57c6ef5b7b', 'aid': '56021', 'src': '9', 'cid': '2836733', 'said': '56021', 'dtstart': '20091225', 'encrypt': '0', 'assign': 'AssignmentTitlec4f211c1-2c38-4daf-86dc-3c57c6ef5b7b', 'uem': 'swgithen@mtu.edu', 'utp': '2', 'fcmd': '2', 'ctl': 'CourseTitle46abd163-7464-4d21-a2c0-90c5af3312ab', 'dtdue': '20100101'}
{rmessage=Successful!, userid=17463581, classid=2836734, assignmentid=7902887, rcode=41}
Adding an assignment with another inst
{'fid': '4', 'diagnostic': '0', 'ufn': 'StevenIU', 'uln': 'GithensIU', 'username': 'sgithens', 'assignid': 'AssignmentTitle2650fcca-b96e-42bd-926e-63660076d2ad', 'aid': '56021', 'src': '9', 'cid': '2836733', 'said': '56021', 'dtstart': '20091225', 'encrypt': '0', 'assign': 'AssignmentTitle2650fcca-b96e-42bd-926e-63660076d2ad', 'uem': 'sgithens@iupui.edu', 'utp': '2', 'fcmd': '2', 'ctl': 'CourseTitle46abd163-7464-4d21-a2c0-90c5af3312ab', 'dtdue': '20100101'}
{rmessage=Successful!, userid=17463581, classid=2836734, assignmentid=7902888, rcode=41}



"""
import unittest
import random
import sys
from org.sakaiproject.component.cover import ComponentManager
from java.net import InetSocketAddress, Proxy, InetAddress
from java.util import HashMap

debug_proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getByName("127.0.0.1"),8008))

tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")

class SakaiUuid(object):
    """My Current Jython impl doens't seem to have UUID, so re-implementing it 
    for now"""

    def __init__(self):
        self.idmanager = ComponentManager.get("org.sakaiproject.id.api.IdManager")

    def uuid1(self):
        return self.idmanager.createUuid()

uuid = SakaiUuid()

def getJavaMap(d=None,**kwargs):
    m = HashMap()
    if d is not None:
        for key,val in d.iteritems():
            m.put(key,val)
    for key,val in kwargs.iteritems():
        m.put(key,val)
    return m

defaults = {
    "aid": "56021",
    "said": "56021",
    "diagnostic": "0",
    "encrypt": "0",
    "src": "9"
}

userdummy = {
    "uem": "swgithenaabb1234124@mtu.edu",
    "ufn": "Stevenaabb1234",
    "uln": "Githensaaabb234",
    "utp": "2",
    "uid": "1979092312341234124aabb",
    "username": "swgithenaabb1234124"
}

user = {
    "uem": "swgithen@mtu.edu",
    "ufn": "Steven",
    "uln": "Githens",
    "utp": "2",
    #"uid": "19790923",
    "username": "swgithen"
}

user2 = {
    "uem": "sgithens@iupui.edu",
    "ufn": "StevenIU",
    "uln": "GithensIU",
    "utp": "2",
    "username": "sgithens"
}

adduser = {
    "fcmd" : "2",
    "fid" : "1"
}

def callTIIReviewServ(params):
    """Use the Sakai Turnitin Service to make a raw call to TII with the 
    dictionary of parameters. Returns the API results in map/dict form."""
    return tiireview_serv.callTurnitinWDefaultsReturnMap(getJavaMap(params))

def makeNewCourseTitle():
    "Make and return a new random title to use for integration test courses"
    return "CourseTitle"+str(uuid.uuid1())

def makeNewAsnnTitle():
    "Make and return a new random title to use for integration test assignments"
    return "AssignmentTitle"+str(uuid.uuid1())

def addSampleInst():
    """This will add/update a user to Turnitin. A successful return looks as 
    follows:
    {rmessage=Successful!, userid=17259618, rcode=11}
    It important to note that the userid returned is the userid of whoever made
    this API call, and not necessarily the user that was just added.
    """
    adduser_cmd = {}
    adduser_cmd.update(adduser)
    adduser_cmd.update(user)
    adduser_cmd.update(defaults)

    return callTIIReviewServ(adduser_cmd)

def addSampleClass():
    """Add a simple class using Sakai Source 9 parameters.
    Successful results should look as follows:
    {rmessage=Successful!, userid=17259618, classid=2833470, rcode=21}
    """
    addclass_cmd = {}
    addclass_cmd.update(user)
    addclass_cmd.update(defaults)
    addclass_cmd.update({
        "ctl": makeNewCourseTitle(),
        "utp":"2",
        "fid":"2",
        "fcmd":"2"
    })
    
    return callTIIReviewServ(addclass_cmd)

def addSampleAssignment():
    """Add a simple assignment."""
    course_title = makeNewCourseTitle()
    
    addclass_cmd = {}
    addclass_cmd.update(user)
    addclass_cmd.update(defaults)
    addclass_cmd.update({
        "ctl": course_title,
        "cid": course_title,
        "utp":"2",
        "fid":"2",
        "fcmd":"2"
    })
    
    print("Adding a class\n"+str(addclass_cmd))
    addclass_results = callTIIReviewServ(addclass_cmd)
    print(addclass_results)
    cid = addclass_results["classid"]
    
    asnn_title = makeNewAsnnTitle()
    
    addasnn_cmd = {}
    addasnn_cmd.update(user)
    addasnn_cmd.update(defaults)
    addasnn_cmd.update({
        "fid":"4",
        "fcmd":"2",
        "ctl":course_title,
        "assign":asnn_title,
        "assignid":asnn_title,
        "utp":"2",
        "dtstart":"20091225",
        "dtdue":"20100101",
        "cid":course_title
        #"ced":"20110101"
    })
    
    print("Adding an assignment\n"+str(addasnn_cmd))
    print(callTIIReviewServ(addasnn_cmd))

    # Trying with a second instructor now
    asnn_title = makeNewAsnnTitle()
    addasnn_cmd = {}
    addasnn_cmd.update(user2)
    addasnn_cmd.update(defaults)
    addasnn_cmd.update({
        "fid":"4",
        "fcmd":"2",
        "ctl":course_title,
        "assign":asnn_title,
        "assignid":asnn_title,
        "utp":"2",
        "dtstart":"20091225",
        "dtdue":"20100101",
        "cid":cid
        #"ced":"20110101"
    })
    
    print("Adding an assignment with another inst\n"+str(addasnn_cmd))
    print(callTIIReviewServ(addasnn_cmd))


# Temporarily change to straight HTTP so I can intercept with WebScarab to get a parameter dump
#tiiresult = tiireview_serv.callTurnitinReturnMap("http://www.turnitin.com/api.asp?",
#                getJavaMap(adduser_cmd), "sakai123", debug_proxy
#                );

class TestRawTurnitinSource9(unittest.TestCase):
    """
    This set of test cases is going to flex using the raw Turnitin API by 
    sending the hand crafted maps to the server and examing the return results.
    
    Additionally all these tests will use the source 9 setup. 
    """

    def setUp(self):
        self.tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")

    def testAdduser(self):
        results = addSampleInst()
        self.assertEquals(results["rmessage"],"Successful!")
        self.assertEquals(results["rcode"],"11")
        
    def testAddclass(self):
        results = addSampleClass()
        self.assertEquals(results["rmessage"],"Successful!")
        self.assertEquals(results["rcode"],"21")


def main(args):
    if len(args) > 0 and args[0] == "runtests":
        print("Running the tests")
        tii_suites = []
        tii_suites.append(unittest.TestLoader().loadTestsFromTestCase(TestRawTurnitinSource9))
        alltests = unittest.TestSuite(tii_suites)
        unittest.TextTestRunner(verbosity=2).run(alltests)
    else:
        addSampleAssignment()

if __name__ == "__main__":
    main(sys.argv[1:])

