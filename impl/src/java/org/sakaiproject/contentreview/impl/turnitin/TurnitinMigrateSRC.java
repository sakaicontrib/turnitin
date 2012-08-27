/**********************************************************************************
 * $URL:
 * $Id:
 ***********************************************************************************
 *
 * Copyright (c) 2006 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.contentreview.impl.turnitin;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This class contains functionality to Migrate OPEN API classes to SRC 9
 *
 * @author Alexander Bell
 *
 */
public class TurnitinMigrateSRC {
    private static final Log log = LogFactory.getLog(TurnitinMigrateSRC.class);

    private TurnitinAccountConnection turnitinConn;
    public void setTurnitinConn(TurnitinAccountConnection turnitinConn) {
            this.turnitinConn = turnitinConn;
    }

    public void init() {
            //If migrate is enabled, then run the migrate call.
            if(turnitinConn.getMigrateSRC()){
                        try{
                            migrateSRC();
                        }catch(Exception e){
                            log.error("Error migrating SRC. "+e.toString());
                        }
            }
    }

    /**
     * Call Turnitin Migrate function
     * @throws SubmissionException
     * @throws TransientSubmissionException
     */
    public void migrateSRC() throws SubmissionException, TransientSubmissionException {
            log.info("Starting SRC Migration");
            Map params = new HashMap();
            params.putAll(turnitinConn.getBaseTIIOptions());
            params.put("fid", "99");
            params.put("fcmd", "2");
            params.put("utp", "2");
            params.put("uem", "fid99@turnitin.com");
            params.put("ufn", "FID99");
            params.put("uln", "Turnitin");

            Document document = null;
                try {
                        document = turnitinConn.callTurnitinReturnDocument(params);
                }
                catch (TransientSubmissionException e) {
                        log.debug("Update failed due to TransientSubmissionException error: " + e.toString());
                }
                catch (SubmissionException e) {
                        log.debug("Update failed due to SubmissionException error: " + e.toString());
                }
                catch(Exception e){
                    log.debug(e.toString());
                }

                Element root = document.getDocumentElement();
                if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("99") == 0) {
                        log.info("SRC Migration Successful");
                } else {
                        log.debug("SRC Migration Not Successful");
                        log.debug(document.getTextContent());
                }
    }
}