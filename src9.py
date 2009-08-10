"""
Test script for src=9 provisioning
"""
import unittest
import random
from org.sakaiproject.component.cover import ComponentManager
from java.net import InetSocketAddress, Proxy, InetAddress
from java.util import HashMap

debug_proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getByName("127.0.0.1"),8008))

tiireview_serv = ComponentManager.get("org.sakaiproject.contentreview.service.ContentReviewService")

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
    "uid": "19790923",
    "username": "swgithen"
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

adduser_cmd = {}
adduser_cmd.update(adduser)
adduser_cmd.update(user)
adduser_cmd.update(defaults)

print("Using Swgithen")

tiiresult = tiireview_serv.callTurnitinWDefaultsReturnMap(getJavaMap(adduser_cmd));

# Temporarily change to straight HTTP so I can intercept with WebScarab to get a parameter dump
#tiiresult = tiireview_serv.callTurnitinReturnMap("http://www.turnitin.com/api.asp?",
#                getJavaMap(adduser_cmd), "sakai123", debug_proxy
#                );

print(tiiresult)

