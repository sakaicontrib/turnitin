package org.sakaiproject.contentreview.mocks;

import java.util.Map;
import java.util.Properties;

import org.sakaiproject.turnitin.util.TurnitinLTIUtil;

public class FakeTiiUtil extends TurnitinLTIUtil{
	
	public String getGlobalTurnitinLTIToolId(){
		return "globalId";
	}
	
	public int makeLTIcall(int type, String urlParam, Map<String, String> ltiProps){
		return 1;//TODO checks o algo?
	}
	
	public Object insertTIIToolContent(String globalToolId, Properties props){
		long i = 12345678910L;
		return i;
	}
	

}