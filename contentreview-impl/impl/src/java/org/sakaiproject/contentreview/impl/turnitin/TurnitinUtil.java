package org.sakaiproject.contentreview.impl.turnitin;

import java.net.Proxy;
import java.util.Map;

/**
 * This class contains utility classes and Methods for the Turnitin 
 * integration. This includes things like a default mapping function(s) to 
 * create unique Titles for TII Assignments, and data for credentials,
 * provisioning, etc.
 * 
 * @author sgithens
 *
 */
public class TurnitinUtil {

    public static String TEST_CID = "***********";
    public static String TEST_CTL = "StevesTestClass2";
    public static String TEST_UEM = "***********";
    public static String TEST_UFN = "Test First Name";
    public static String TEST_ULN = "Test Last Name"; 
    public static String TEST_UPW = "***********";
    public static String TEST_UID = "***********";
    public static String TEST_AID = "***********";
    public static String TEST_SHARED_SECRET = "***********";
    public static String TEST_APIURL = "https://www.turnitin.com/api.asp?";
    public static Proxy TEST_PROXY = null;

    public static String[] mapToStringArray(Map map) {
        if (map == null) {
            return new String[]{};
        }
        
        String[] togo = new String[map.size()*2];
        
        int count = 0;
        for (Object key: map.keySet()) {
            togo[count] = key.toString();
            count++;
            togo[count] = map.get(key).toString();
            count++;
        }
        
        return togo;
    }
}
