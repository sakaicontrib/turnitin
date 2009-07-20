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

    public static Proxy TEST_PROXY = null;
    
    public static final String PROP_TURNITIN_GENERATE_FIRST_NAME = "turnitin.generate.first.name";
    public static final String PROP_TURNITIN_GET_REPORTS_BULK = "turnitin.getReportsBulk";
    public static final String PROP_TII_CHECK_WORD_LENGTH = "tii.checkWordLength";
    public static final String PROP_TURNITIN_UPDATE_ASSINGMENTS = "turnitin.updateAssingments";
    public static final String PROP_TURNITIN_MAX_FILE_SIZE = "turnitin.maxFileSize";
    public static final String PROP_TURNITIN_MAX_RETRY = "turnitin.maxRetry";
    public static final String PROP_TURNITIN_DEFAULT_INSTRUCTOR_ID = "turnitin.defaultInstructorId";
    public static final String PROP_TURNITIN_DEFAULT_CLASS_PASSWORD = "turnitin.defaultClassPassword";
    public static final String PROP_TURNITIN_DEFAULT_ASSIGN_ID = "turnitin.defaultAssignId";
    public static final String PROP_TURNITIN_SEND_NOTIFATIONS = "turnitin.sendnotifations";
    public static final String PROP_TURNITIN_DEFAULT_INSTRUCTOR_PASSWORD = "turnitin.defaultInstructorPassword";
    public static final String PROP_TURNITIN_DEFAULT_INSTRUCTOR_LAST_NAME = "turnitin.defaultInstructorLName";
    public static final String PROP_TURNITIN_DEFAULT_INSTRUCTOR_FIRST_NAME = "turnitin.defaultInstructorFName";
    public static final String PROP_TURNITIN_DEFAULT_INSTRUCTOR_EMAIL = "turnitin.defaultInstructorEmail";
    public static final String PROP_TURNITIN_API_URL = "turnitin.apiURL";
    public static final String PROP_TURNITIN_SECRET_KEY = "turnitin.secretKey";
    public static final String PROP_TURNITIN_SAID = "turnitin.said";
    public static final String PROP_TURNITIN_AID = "turnitin.aid";
    public static final String PROP_HTTP_PROXY_PORT = "http.proxyPort";
    public static final String PROP_HTTP_PROXY_HOST = "http.proxyHost";
    public static final String PROP_TURNITIN_PROXY_HOST = "turnitin.proxyHost";
    public static final String PROP_TURNITIN_PROXY_PORT = "turnitin.proxyPort";
    
    //TODO - do we need these as properties??
    public static final String PROP_TURNITIN_CID = "turnitin.cid";
    public static final String PROP_TURNITIN_CTL = "turnitin.ctl";

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
