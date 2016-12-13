package org.sakaiproject.contentreview.mocks;

import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeBreakdown;

public class FakeTime implements Time
{
	public String toStringSql()	{ return null; }

	public String toStringLocal() 	{ return null; }

	public String toStringGmtFull() { return null; }

	public String toStringLocalFull(){ return null; }

	public String toStringLocalFullZ(){ return null; }

	public String toStringGmtShort(){ return null; }

	public String toStringLocalShort(){ return null; }

	public String toStringGmtTime(){ return null; }

	public String toStringLocalTime(){ return null; }

	public String toStringLocalTime24(){ return null; }

	public String toStringLocalTimeZ(){ return null; }

	public String toStringGmtDate(){ return null; }

	public String toStringLocalDate(){ return null; }

	public String toStringLocalShortDate(){ return null; }

	public String toStringRFC822Local(){ return null; }

	public String toStringFilePath(){ return null; }

	public void setTime(long l){ return; }

	public long getTime(){ return 0; }

	public boolean before(Time time){ return true; }

	public boolean after(Time time){ return true; }

	public Object clone(){ return null; }

	public TimeBreakdown breakdownGmt(){ return null; }

	public TimeBreakdown breakdownLocal(){ return null; }

	public String getDisplay(){ return null; }
	
	public int compareTo(Object o) { return 0; }
}
