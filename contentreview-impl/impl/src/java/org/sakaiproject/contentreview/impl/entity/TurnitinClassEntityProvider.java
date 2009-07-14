package org.sakaiproject.contentreview.impl.entity;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.sakaiproject.contentreview.impl.turnitin.TurnitinAPIUtil;
import org.sakaiproject.contentreview.impl.turnitin.TurnitinUtil;
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
            TurnitinAPIUtil.createAssignment(TurnitinUtil.TEST_CID, TurnitinUtil.TEST_CTL, ref.getId(), 
                    ref.getId(), TurnitinUtil.TEST_UEM, TurnitinUtil.TEST_UFN, 
                    TurnitinUtil.TEST_ULN, TurnitinUtil.TEST_UPW, TurnitinUtil.TEST_UID, 
                    TurnitinUtil.TEST_AID, TurnitinUtil.TEST_SHARED_SECRET, 
                    TurnitinUtil.TEST_AID, TurnitinUtil.TEST_APIURL, 
                    TurnitinUtil.TEST_PROXY, extra.toArray(new String[] {}));
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
                       TurnitinUtil.TEST_CID, // cid
                       TurnitinUtil.TEST_CTL, // ctl 
                       ref.getId(), // assignid
                       ref.getId(), // assignTitle
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
