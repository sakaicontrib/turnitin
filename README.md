Turnitin Content Review Service
===============================

2016 TII LTI integration
------------------------
First step: select the ContentReviewSiteAdvisor you want on the components.xml file. Uncomment the specific lines and deploy the tool.

SITEPROPERTYADVISOR:
--------------------
The new TII LTI integration includes some new site properties needed for configuration. If they aren't added to the site, the default value will be used:

	* useContentReviewService : this site property indicates whether we are using the TII integration or not
		Default value: false
	
	* useContentReviewLTIService : this site property indicates whether we are using the new LTI TII integration or the old API
		Default value: false
		Note: it will only be checked if the previous property is true
		
	* useContentReviewDirectSubmission : this site property indicates whether we are using the direct/external submission way or the Sakai submission method
		Default value: false
		Note: it will only be checked if the previous properties are true
	
	For instance, if we want to set up a new site for using the new LTI integration we'd need these properties:

		useContentReviewService = true
		useContentReviewLTIService = true
	
GLOBALPROPERTYADVISOR:
----------------------
This advisor will be used when we want all our sites to behave the same way. If they aren't included to the sakai.properties file, false will be taken as the default value.

	* assignment.useContentReviewService : this property indicates whether we are using the TII integration or not
	
	* assignment.useContentReviewLTI : this property indicates whether we are using the new LTI TII integration or the old API
		Note: it will only be checked if the previous property is true
		
	* assignment.useContentReviewDirect : this property indicates whether we are using the direct/external submission way or the Sakai submission method
		Note: it will only be checked if the previous properties are true
	
	For instance, if we want all the sites to use the new LTI integration, we'd need these properties (besides all the other TII configuration):

		assignment.useContentReviewService = true
		assignment.useContentReviewLTI = true


LTI GLOBAL TOOL CONFIGURATION:
------------------------------
Launch URL -> https://turnitinuk.com/api/lti/1p0/assignment
Launch Key -> from the turnitin setup
Launch Secret -> from the turnitin setup
Send Names (ticked)
Send Emails (ticked)
Allow custom parameters (ticked)
Allow tool title to be changed (ticked)
Tool Visibility (stealthed)
It must have a site ID of !turnitin


=============================== Old readme content

Installation
------------

This module can be installed with the standard Sakai maven idiom.
Additionally, it can be included with a Sakai distribution as a top
level module as is typical with other 3rd party modules and tools.

The standard build command is the same:

::

  mvn clean install sakai:deploy

Post Install Configuration
--------------------------

After compiling and installing the code you need to add some
sakai.properties with your account information, and then set up
some quartz jobs that submit and fetch papers from the Turnitin
web service.

Sakai Properties
~~~~~~~~~~~~~~~~

Sakai Turnitin Content Review configurations typically fall in to 
2 categories.  The original iteration of development had a single 
instructor account that controlled all the classes for the integration.
More recent versions have allowed using an option that enables
each instructor in Sakai to have a fully provisioned account as
well which makes some tighter integration options possible.

We'll cover the newer setup first.  Assuming you have a Turnitin
Account/Contract already, you'll need to log in to turnitin.com
and create a new subaccount on your main account. You'll then need
to configure the integration to use the Open API.  Very soon there
will be an option there for Sakai (along with the other Course Management
Systems), but until then you'll need to email David Wu at Turnitin
and request that this account be "Source 9 Enabled".  His email is
davidw At iparadigms Dot com

You'll need the shared key and Account ID for the properties.

The properties then are as follows:

:: 

  turnitin.enable.assignment2=true # This assumes you are using Assignments 2
  turnitin.apiURL=https://www.turnitin.com/api.asp?
  turnitin.secretKey=mysecret # This is the secret you set online.
  turnitin.said=12345 # These are the 5 digit account
  turnitin.aid=12345  # ID you had enabled
  turnitin.useSourceParameter=true

If you wish to use (or are currently) a system with one Turnitin Instructor
account provisioning all the assignments, the settings are as follows.

:: 
  
  TODO

Usefull logging settings for this project include:

DEBUG.org.sakaiproject.contentreview.impl.turnitin
DEBUG.org.sakaiproject.turnitin.util.TurnitinAPIUtil.apicalltrace

Quartz (Cron) Jobs
~~~~~~~~~~~~~~~~~~

There are 2 mandatory quartz jobs that need to be set up, and a third
Please see either the readme.txt or readme.html in the docs folder.
