package org.sakaiproject.contentreview.impl.entity;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.sakaiproject.contentreview.impl.turnitin.TurnitinAPIUtil;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.entityprovider.CoreEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.CRUDable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.RESTful;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Updateable;
import org.sakaiproject.entitybroker.entityprovider.search.Search;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;

public class TurnitinClassEntityProvider extends AbstractEntityProvider
implements Updateable {
    public static String PREFIX = "turnitin-class";
    
    private static String TEST_CID = "***********";
    private static String TEST_CTL = "StevesTestClass2";
    private static String TEST_UEM = "***********";
    private static String TEST_UFN = "Test First Name";
    private static String TEST_ULN = "Test Last Name"; 
    private static String TEST_UPW = "***********";
    private static String TEST_UID = "***********";
    private static String TEST_AID = "***********";
    private static String TEST_SHARED_SECRET = "***********";
    private static String TEST_APIURL = "https://www.turnitin.com/api.asp?";
    private static Proxy TEST_PROXY = null;

    public String getEntityPrefix() {
        return PREFIX;
    }

    public void updateEntity(EntityReference ref, Object entity,
            Map<String, Object> params) {
        
        List<String> extra = new ArrayList<String>();
        for (String key: params.keySet()) {
            extra.add(key); extra.add(params.get(key).toString());
        }
        
        try {
            TurnitinAPIUtil.createAssignment(TEST_CID, TEST_CTL, ref.getId(), 
                    ref.getId(), TEST_UEM, TEST_UFN, TEST_ULN, TEST_UPW, TEST_UID, 
                    TEST_AID, TEST_SHARED_SECRET, 
                    TEST_AID, TEST_APIURL, TEST_PROXY, extra.toArray(new String[] {}));
        } catch (SubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TransientSubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    public Object getEntity(EntityReference ref) {
        String assignid = ref.getId();
        Map togo = null;
        try {
            Map retdata = TurnitinAPIUtil.getAssignment(
                       TEST_CID, // cid
                       TEST_CTL, // ctl 
                       ref.getId(), // assignid
                       ref.getId(), // assignTitle
                       TEST_UEM, // uem
                       TEST_UFN, //ufn
                       TEST_ULN,  //uln
                       TEST_UPW, // upw
                       TEST_UID, // uid
                       TEST_AID, // aid
                       TEST_SHARED_SECRET, // shared secret
                       TEST_AID, //sub account id
                       TEST_APIURL, // api url
                       TEST_PROXY // proxy
            );
            if (retdata.containsKey("object")) {
                togo = (Map) retdata.get("object");
            }
        } catch (TransientSubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return togo;
    }

}
