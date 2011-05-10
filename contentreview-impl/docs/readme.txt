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
for Turnitin in it's own interface.

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
account provisioning all the assignments, the settings are as follows. These
are mostly for older installations. If you are starting from scratch you should
use the above settings.

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
with extending the functionality and fix bugs. It also contains details
on how we call the Turnitin API and which calls and order we send them
over the wire in.

Turnitin.com provides a web interface to the plagiarism checking service,
but also a set of API's that roughly mimic the interaction of the Web
Based GUI. Our interaction with turnitin is modeled by a state machine 
idiom. Because it can take a while for turnitin to process and index the
contents of student's papers (especially if you are submitting large amounts
of them), the results are not available immediately in the Sakai User
Interface. What happens is that we store the information in a table row,
and then we have 2 quartz (cron) jobs that periodically poll the rows and
see if they can take any action. Additionally, having the turnitin calls
processed in the background ensures we won't have too much extra latency
for our user interface operations in the Assignments application.

The first quartz job is responsible for submitting the actual paper contents
to turnitin. When a student submits a Content Review enabled assignment (
in either Assignments 1 or 2), a new database row is added to the 
CONTENT_REVIEW_ITEM table. This contains a good deal of information about 
this specific paper. There is a row in the table for each paper to be checked.
So in Assignments 2, if a student attaches multiple papers to an assignment,
each of those will have it's own row.  So, this first quartz job scans the
table for jobs that need to be submitted to Turnitin still (based off the
status column), and for each one sends it over the wire to Turnitin. It then
records whether the call was successful or not, and records any errors that
occurred. At this point, if everything worked ok, the paper is now on
Turnitin's servers waiting to be processed. 

The second quartz job, Review Reports, checks in on Turnitin to see if it 
has finished processing 

API Call Traces
~~~~~~~~~~~~~~~

This section includes traces of calls we make to turnitin.

Adding an Assignment (with class, user)
***************************************

When adding an assignment, the calls to Turnitin actually occur during
the assignment addition rather than in a background job. We have support 
built to deal with failures that occurs and automatically timing out if
the out of band call to Turnitin takes too long.

The options available in Assignments 2 are depicted below.

.. image:: images/assignment2-authoring.png
  
This will create the class and user if they do not exist first.

Fids and fcmds used:

- 4-7
- 1-2
- 17-2
- 4-2
- 4-3
- 18-2

`Full trace of adding an assignment. <creating-assignment-trace.txt>`_

Reading an Assignment
*********************

The code for reading an assignment is very close to authoring, essentially 
we just fetch the information since the assignment alreay exists.

Fids/Fcmds used:

- 4-7

`Full trace of reading an existing assignment. <reading-assignment-data.txt>`_

Submitting Papers to Queue
**************************

This happens in the background after students submit papers.  The student 
submission screen is pictured below. Each attachment is added to the 
queue table to be run in this job.

.. image:: images/submitting-assignment.png

Fids/Fcmds used:

- 3-2
- 5-2

`Full trace of submitting papers. <submitting-document-to-turnitin.txt>`_

Fetching Reports 
****************

When papers have been finished being processed instructors (and students if 
that option is selected) see percentage bars with the plagiarism score. Clicking on
the bar brings you to the report at the turnitin.com site.

.. image:: images/generated-reports.png

Fids/Fcmds used:

- 4-7
- 10-2

`Full trace of fetching reports. <fetching-reports-from-turnitin.txt>`_

Syncing Rosters
***************

This requires a bit of an explanation. We have another quartz job that runs in 
the background that is related to provisioning. We have a registered event
listener in Sakai that observers membership role changes occuring from Site Setup
or other tools. ( This is similar to other roster sync events sometimes used
in modules in Sakai. ) However, we don't sync the roles during the actual event
but had the site to a sync table. This is so we don't add extra time to the UI
operation with the out of band call to turnitin.com. ( This also means it's
important to run the sync quartz job regularly. )

There are also a few caveats to note. The provisioning functionality doesn't 
completely match up between Sakai and Turnitin, so we have to make up for
a few things. The major item is that a user can be both a Student and an Instructor
in a Turnitin site. This creates a possible security issue if the following
events were to occur:

1. A Student submits an assignment in Assignment 2, at which point they are 
   initially provisioned in the Turnitin.com site as a student.
#. For some reason the student gets changed to an instructor (or other elevated)
   role in the site and accesses the Assignment 2 site, at which point they
   are provisioned as an instructor in the same Turnitin.com site.
#. Someone notices this error and switches them back to being a student in the
   Sakai site. However, if they log in to turnitin.com to access the site, they 
   are both a student and an instructor, and thusly can view materials they 
   shouldn't have access to.

Because of this scenerio, when we sync the Sakai and Turnitin sites, we download the 
entire class list from Turnitin, parse it, and check for any duplicate roles, etc
and make the correct calls to adjust everyones role.

Fids/Fcmds used:

- 19-5
- 19-3


`Full trace of syncing rosters. <roster-sync-with-turnitin.txt>`_

Turnitin Quick API Call Reference
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

========  ===============================
Fid-Fcmd  Description
========  ===============================
1-2       Create User
3-2       Join a Class
4-2       Create a New Assignment
4-3       Modify an existing Assignment
4-7       Return Assignment metadata
5-2       Submit paper for processing
10-2      List submissions for course
17-2      Login, Create Turnitin Session
18-2      Logout of Turnitin Session
19-5      Get list of course enrollment
19-3      Switch the users role in course
========  ===============================

Release Notes
-------------

.. Header levels = -  ~  *
