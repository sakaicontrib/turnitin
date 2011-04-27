Turnitin Content Review Service
===============================

.. author Steven Githens

.. contents::

Overview
--------

The Turnitin Content Review service is an implementation of the
Content Review API's for Sakai. Essentially it allows assignments
from Assignments 1 or Assignments 2 to be checked for plagiarism
using the commercial Turnitin.com service.

The integration with Assignments 1, and especially 2 is fairly
deep, ie. it's not just an iframe of the Turnitin Site embedded
in Sakai. Assignments 2 exposes a fairly large number of options
for Turnitin in it's own interface.  For example, the assignment
authoring page options for an instructor are as below:

TODO Insert Screenshot

This integration was originally written by INSERT NAME at the
University of Cape Town.  Current maintainers, contributers, and
developers include:

- David Horwitz *lead*
- Steven Githens *lead*
- Michelle Wagner
- Numberous folks at deployments.

Let me know if I someone should be added.

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
2 categories.  Older installations typically had a single 
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

Usefull logging settings for this project include (these are using the 
standard Sakai log4j syntax, so you can just add them to the count 
if you already have some.):

:: 

  log.config.count = 2
  log.config.1 = DEBUG.org.sakaiproject.contentreview.impl.turnitin
  log.config.2 = DEBUG.org.sakaiproject.turnitin.util.TurnitinAPIUtil.apicalltrace

Quartz (Cron) Jobs
~~~~~~~~~~~~~~~~~~

There are 2 mandatory quartz jobs that need to be set up, and a third
if you are using Instructor Provisioning.

- | TII Content Review Queue 
  | Typical Cron Expression: 0 0/5 * * * ?
- | Process Content Review Reports
  | Typical Cron Expression: 0 0/5 * * * ?

If you are using Instructor Provisioning, then also set up:

- | Process Turnitin Content Review Roster Sync
  | Typical Cron Expression: 0 0/1 * * * ?

Enabling Turnitin on Specific Sites
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Out of the box, Turnitin integration is enabled on all sites in the Sakai
Instance. It is possible to restrict this by using a different 
ContentReviewSiteAdvisor, which can be changed in the components.xml of 
the pack module.

The other 2 that exist are the SitePropertyAdvisor and SiteCourseTypeAdvisor.

If the SiteCourseTypeAdvisor is enabled, Turnitin integration will only
be available on Course sites. 

If the SitePropertyAdvisor is used, the integration will only be 
available on sites with the following property set to true (on the site
itself)

:: 

  useContentReviewService = true


FAQ
---

Users must have all account fields filled in

Can't use same accounts across test/dev/prod nodes

docx but really doc so it won't work

My reports are taking a long time to generate.

Development Documentation
-------------------------

This section contains documentation for developers who want to work 
extending the functionality and fix bugs.

Release Notes
-------------
