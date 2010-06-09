package org.sakaiproject.contentreview.impl.turnitin;

import java.util.Observable;
import java.util.Observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.Event;

public class MembershipChangeObserver implements Observer {
    private static final Log log = LogFactory.getLog(MembershipChangeObserver.class);
    
    public static final String MEMBERSHIP_EVENT = "site.upd.site.mbrshp";
    
    private EventTrackingService eventTrackingService;
    public void setEventTrackingService(EventTrackingService eventTrackingService) {
        this.eventTrackingService = eventTrackingService;
    }
    
    private TurnitinRosterSync turnitinRosterSync;
    public void setTurnitinRosterSync(TurnitinRosterSync turnitinRosterSync) {
        this.turnitinRosterSync = turnitinRosterSync;
    }
    
    public void init() {
        eventTrackingService.addPriorityObserver(this);
    }
    
    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof Event) {
            Event event = (Event) arg;
            if (event.getEvent().equals(MEMBERSHIP_EVENT)) {
                log.info("TurnitinRoster: Handling membership event.");
                // TODO Check and see if the site is even registered to use
                // turnitin, they we don't have to call TII for every site
                turnitinRosterSync.syncSiteWithTurnitin(event.getContext());
            }
        }
    }

}
