package org.sakaiproject.turnitin.api;

import java.util.Map;

/**
 * Small API to allow code sharing between impl and tool
 */
public interface TurnitinLTIAPI {

    String getGlobalSecret();

    String getGlobalKey();
}
