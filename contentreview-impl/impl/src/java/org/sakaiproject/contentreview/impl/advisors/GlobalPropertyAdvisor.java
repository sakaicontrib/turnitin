package org.sakaiproject.contentreview.impl.advisors;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.contentreview.service.ContentReviewSiteAdvisor;
import org.sakaiproject.site.api.Site;

public class GlobalPropertyAdvisor implements ContentReviewSiteAdvisor {

	private static final Log LOG = LogFactory.getLog(GlobalPropertyAdvisor.class);
	
	private String tiiProperty;
	public void setTiiProperty(String p){
		tiiProperty = p;
	}
	
	private String tiiLTIProperty;
	public void setTiiLTIProperty(String p){
		tiiLTIProperty = p;
	}
	
	private String tiiDirectSubmissionProperty;
	public void setTiiDirectSubmissionProperty(String p){
		tiiDirectSubmissionProperty = p;
	}
	
	private String tiiLTICutoverDateProperty;
	public void setTiiLTICutoverDateProperty(String p)
	{
		tiiLTICutoverDateProperty = p;
	}
	
	private ServerConfigurationService serverConfigurationService;
	public void setServerConfigurationService(ServerConfigurationService s){
		serverConfigurationService = s;
	}
	
	public boolean siteCanUseReviewService(Site site) {
		return serverConfigurationService.getBoolean(tiiProperty, false);
	}
	
	public boolean siteCanUseLTIReviewService(Site site) {
		return serverConfigurationService.getBoolean(tiiLTIProperty, false);
	}
	
	@Override
	public boolean siteCanUseLTIReviewServiceForAssignment(Site site, Date assignmentCreationDate)
	{
		boolean canUseLTI = siteCanUseLTIReviewService(site);
		String cutoverDateStr = serverConfigurationService.getString(tiiLTICutoverDateProperty);
		if (StringUtils.isBlank(cutoverDateStr))
		{
			return canUseLTI;
		}
		
		DateFormat df = new SimpleDateFormat("yyyy-MMM-dd");
		try
		{
			Date cutover = df.parse(cutoverDateStr);
			return canUseLTI && assignmentCreationDate.after(cutover);
		}
		catch (ParseException e)
		{
			LOG.error("Unable to parse cutover date from property: " + cutoverDateStr, e);
			return canUseLTI;
		}
	}
	
	public boolean siteCanUseLTIDirectSubmission(Site site){
		return serverConfigurationService.getBoolean(tiiDirectSubmissionProperty, false);
	}

}
