The new implementation of turnitin for Sakai 11.0

This is based on Oxford's code, but with slight changes for
the current state of 11.x ,and a couple of bug fixes and
improvements. This will track Rutgers production code.

* apply diff to your source
* then completely replace content-review/contentreview-impl with the copy here
* sakai.properties has the properties I used for debugging. This includes
  our properties for the old implementation, so it may have more than you need

Steps to make this work:

* Have your TurnitIn administrator contact Turnitin to enable the LTI integration for you. Once they have, your administrator will need to login, go to integrations, and configure it. The output of this will be a secret key (which you choose) and the numeric account number of your account at TII.
* Once you’ve built the code and started Sakai, login as admin. You need to add Turnitin as an LTI tool in “External tools”. Here’s Oxford’s instructions:
* Remembers to set up Quartz jobs
** Content Review Queue
** Content Review Reports
You will have to run jobs manually unless they're set up to auto-run. for testing I've been running them manually when needed

LTI setup

* As admin, create a site that you’ll install the turnitin API tool into. If you can manage to set the siteid to !turnitin, do so. For me the admin site interface doesn’t let me choose the site ID. Once you create the site, remember the ID.
* Add an LTI tool via Admin Workspace > External Tools. The configuration for the tool on LIVE should be

Properties for LTI tool:

* Site ID: !turnitin [or whatever site ID your site uses]
* Tool Title: Turnitin
* Allow tool title to be changed
* Set Button Text - Turnitin
* Do not allow button text to be changed
* Description - optional
* Tool status: Enabled
* Tool visibility: stealthed [I don’t recommend this. It makes it hard to add the tool to the site]
* Launch URL - https://api.turnitinuk.com/api/lti/1p0/assignment or equivalent for the US, Spain etc.

For most people this will be https://api.turnitin.com/api/lti/1p0/assignment

* Do not allow URL to be changed
* Tool Key - nnnn - your Turnitin account number
* Do not allow Launch Key to be changed
* Secret - the secret your Turnitin administrator created when configuring the LTI API
* Do not allow Launch Secret to be changed
* Do not allow frame height to be changed
* Open in a New Windows - checked.
* Send Names to the External Tool - checked.
* Send Email Addresses to the External Tool. - checked.
* Allow External Tool to return grades - checked.
* Never Launch in pop-up
* (In production) Never launch in debug mode
* Allow additional custom parameters ********* very critical

For test, use  https://sandbox.turnitin.com/api/lti/1p0/assignment, though I'm not sure what kind of arrangements you need with Turnitin for that to work. We used the production URL.

Note that properties aren’t identical in the current version of 11 to the list above, but I think this is clear enough.

More details at: http://turnitin.com/en_us/support/integrations/lti/

Starting

Once you’re created the LTI tool, go to the turnitin Sakai site and add the tool. You should be able to do add tool. Turnitin should show under "plugins" in the tool list, unless you stealthed it.

Configure turnitin in sakai.properties and restart. See sakai.properties here.

That file includes the values we were using for the old interface. I’m not sure whether you can remove them or not.

