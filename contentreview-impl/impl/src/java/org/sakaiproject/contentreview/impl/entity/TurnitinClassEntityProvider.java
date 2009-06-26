package org.sakaiproject.contentreview.impl.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        
        
        
    }

    public Object getEntity(EntityReference ref) {
        return new HashMap();
    }

}
