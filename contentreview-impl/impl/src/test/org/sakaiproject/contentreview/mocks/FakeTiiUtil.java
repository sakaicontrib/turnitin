package org.sakaiproject.contentreview.mocks;

import java.util.Map;
import java.util.Properties;

import org.sakaiproject.turnitin.util.TurnitinLTIUtil;
import org.sakaiproject.turnitin.util.TurnitinReturnValue;

public class FakeTiiUtil extends TurnitinLTIUtil{
	
	public String getGlobalTurnitinLTIToolId(){
		return "globalId";
	}

	public String getGlobalTurnitinReportsLTIToolId(){
		return "globalReportsId";
	}
	
	public TurnitinReturnValue makeLTIcall(int type, String urlParam, Map<String, String> ltiProps){
		TurnitinReturnValue retVal = new TurnitinReturnValue();
		retVal.setResult( 1 );
		return retVal;
	}
	
	public Object insertTIIToolContent(String globalToolId, Properties props){
		long i = 12345678910L;
		return i;
	}
	

}
