package org.sakaiproject.contentreview.impl.entity;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
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
import uk.org.ponder.arrayutil.ArrayUtil;


public class TurnitinAssignmentEntityProvider extends AbstractEntityProvider
implements CRUDable {
    private static final Log log = LogFactory.getLog(TurnitinAssignmentEntityProvider.class);
    
    public static String PREFIX = "turnitin-assignment";

    public String getEntityPrefix() {
        return PREFIX;
    }

    public String createEntity(EntityReference ref, Object entity,
            Map<String, Object> params) {
        Map entityMap = (Map) entity;
        Map tiiopts = new HashMap();

        String[] tiioptKeys = new String[] { "submit_papers_to", "rep_gen_speed",
                "s_paper_check", "internet_check", "journal_check", "institution_check"
        };

        for (Object key: entityMap.keySet()) {
            if (ArrayUtil.contains(tiioptKeys, key)) {
                if (entityMap.get(key) instanceof Boolean) {
                    if (((Boolean) entityMap.get(key)).booleanValue()) {
                        tiiopts.put(key, "1");
                    }
                    else {
                        tiiopts.put(key, "0");
                    }
                }
                else {
                    tiiopts.put(key.toString(), entityMap.get(key).toString());
                }
            }
        }

        try {
            TurnitinAPIUtil.createAssignment(
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_CID), // cid
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_CTL), // ctl 
                    ref.getId(), // assignid
                    ref.getId(), // assignTitle
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_EMAIL), // uem
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_FIRST_NAME), //ufn
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_LAST_NAME),  //uln
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_PASSWORD), // upw
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_ID), // uid
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_AID), // aid
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_SECRET_KEY), // shared secret
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_SAID), //sub account id
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_API_URL), // api url
                    TurnitinUtil.TEST_PROXY, // proxy
                    TurnitinUtil.mapToStringArray(tiiopts)
            );
        } catch (SubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TransientSubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return ref.getId();
    }

    public Object getSampleEntity() {
        // TODO Auto-generated method stub
        return null;
    }

    public void updateEntity(EntityReference ref, Object entity,
            Map<String, Object> params) {
        // TODO Auto-generated method stub

    }

    public Object getEntity(EntityReference ref) {
        Map togo = new HashMap();
        try
        {
            Map asnndata =  TurnitinAPIUtil.getAssignment(
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_CID), // cid
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_CTL), // ctl 
                    ref.getId(), // assignid
                    ref.getId(), // assignTitle
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_EMAIL), // uem
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_FIRST_NAME), //ufn
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_LAST_NAME),  //uln
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_PASSWORD), // upw
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_DEFAULT_INSTRUCTOR_ID), // uid
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_AID), // aid
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_SECRET_KEY), // shared secret
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_SAID), //sub account id
                    serverConfigurationService.getString(TurnitinUtil.PROP_TURNITIN_API_URL), // api url
                    TurnitinUtil.TEST_PROXY // proxy
            );
            
            if (asnndata.get("rcode").equals("85")) {
                Map object = (Map) asnndata.get("object");
                if (object.get("searchpapers").equals("1")) {
                    togo.put("s_paper_check", Boolean.TRUE);
                }
                else {
                    togo.put("s_paper_check", Boolean.FALSE);
                }
                
                if (object.get("searchinternet").equals("1")) {
                    togo.put("internet_check", "1");
                }
                else {
                    togo.put("internet_check", "0");
                }
            }
            else {
                log.error("Bad return code in TIIAsnnEntProvider: " + asnndata.get("rcode"));
            }
        } catch (TransientSubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SubmissionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        };
        
        return togo;
    }

    public void deleteEntity(EntityReference ref, Map<String, Object> params) {
        // TODO Auto-generated method stub

    }
    
    private ServerConfigurationService serverConfigurationService;
    public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        this.serverConfigurationService = serverConfigurationService;
    }

}
