"""
Test script for src=9 provisioning
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
    "uem": "swgithen@mtu3.edu",
    "ufn": "Steven3",
    "uln": "Githens3",
    "utp": "2",
    #"uid": "19790923",
    "username": "swgithen23"
}

user2 = {
    "uem": "swgithen@iupui.edu",
    "ufn": "Steven",
    "uln": "Githens",
    "utp": "2",
    "username": "swgithen"
}

adduser = {
    "fcmd" : "2",
    "fid" : "1"
}


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

    return tiireview_serv.callTurnitinWDefaultsReturnMap(getJavaMap(adduser_cmd));

def addSampleClass():
    """Add a simple class using Sakai Source 9 parameters.
    Successful results should look as follows:
    {rmessage=Successful!, userid=17259618, classid=2833470, rcode=21}
    """
    addclass_cmd = {}
    addclass_cmd.update(user)
    addclass_cmd.update(defaults)
    addclass_cmd.update({
        "ctl":"CourseTitle"+str(uuid.uuid1()),
        "utp":"2",
        "fid":"2",
        "fcmd":"2"
    })
    
    return tiireview_serv.callTurnitinWDefaultsReturnMap(getJavaMap(addclass_cmd))


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
        print(addSampleClass())

if __name__ == "__main__":
    main(sys.argv[1:])

