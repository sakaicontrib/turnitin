/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/turnitin/trunk/contentreview-impl/impl/src/java/org/sakaiproject/contentreview/impl/turnitin/TurnitinReviewServiceImpl.java $
 * $Id: TurnitinReviewServiceImpl.java 69345 2010-07-22 08:11:44Z david.horwitz@uct.ac.za $
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
package org.sakaiproject.turnitin.util;

public enum TIIParam {
    aid,
    ainst,
    assign,
    assignid,
    ced,
    cid,
    cpw,
    ctl,
    diagnostic,
    dis,
    dtdue,
    dtstart,
    encrypt,
    fcmd,
    fid,
    gmtime,
    newassign,
    newupw,
    oid,
    pdata,
    pfn,
    pln,
    ptl,
    ptype,
    said,
    sessionId,
    tem,
    uem,
    ufn,
    uid,
    uln,
    upw,
    username,
    utp;
    
    public String toString() {
        // Unfortunately you can't put dashes in enum constant names, so this
        // special case is for the newly added session-id TII Parameter.
        if (name().equals("sessionId")) {
            return "session-id";
        }
        else {
            return name();
        }
    }
}
