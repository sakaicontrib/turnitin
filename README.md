# The new implementation of turnitin for Sakai 11.0

This is based on Oxford's code, but with slight changes for
the current state of 11.x ,and a couple of bug fixes and
improvements. This will track Rutgers production code.

* apply diff to your source
* then completely replace content-review/contentreview-impl with the copy here
* sakai.properties has the properties I used for debugging. This includes
  our properties for the old implementation, so it may have more than you need

## Steps to make this work:

* Have your TurnitIn administrator contact Turnitin to enable the LTI integration for you. Once they have, your administrator will need to login, go to integrations, and configure it. The output of this will be a secret key (which you choose) and the numeric account number of your account at TII.
* Once you’ve built the code and started Sakai, login as admin. You need to add Turnitin as an LTI tool in “External tools”. Here’s Oxford’s instructions:
* Remembers to set up Quartz jobs
** Content Review Queue
** Content Review Reports
You will have to run jobs manually unless they're set up to auto-run. for testing I've been running them manually when needed

## LTI setup

* As admin, create a site that you’ll install the turnitin API tool into. If you can manage to set the siteid to !turnitin, do so. For me the admin site interface doesn’t let me choose the site ID. Once you create the site, remember the ID.
* Add an LTI tool via Admin Workspace > External Tools. The configuration for the tool on LIVE should be

## Properties for LTI tool:

* Site ID: !turnitin [or whatever site ID your site uses]
* Tool Title: Turnitin
* Allow tool title to be changed
* Set Button Text - Turnitin
* Do not allow button text to be changed
* Description - optional
* Tool status: Enabled
* Tool visibility: stealthed [I don’t recommend this. It makes it hard to add the tool to the site]
* Launch URL - https://api.turnitinuk.com/api/lti/1p0/assignment or equivalent for the US, Spain etc.

## For most people this will be https://api.turnitin.com/api/lti/1p0/assignment

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

## Sakai.properties

The following sakai.properties are relevant to the Turnitin LTI integration:

##### turnitin.lti.site
* This is the ID of the LTI tool you created above (likely "!turnitin)
* There is no default for this property

##### turnitin.lti.globalCreate
* This boolean property if set to true will check if the LTI tool exists and if it doesn't create it.
* Defaults to false

##### turnitin.lti.globalCreate.secretKey
* If you're autocreating the LTI tool, this is the secret that is used to create the tool
* There is no default for this property 

##### turnitin.grademark.integration.enabled
* Enables or disables the ability for Sakai to receive Grademark grades from Turnitin
* Defaults to "true"

##### turnitin.maxRetry
* Defines the maximum number of times an item (submission) will attempt to be submitted to Turnitin
* Defaults to "100"

##### contentreview.site.min=5
* Defines the minimum number of characters allowed in a site title by Turnitin
* Defaults to "0"
* You **must** set this to 5 for use with Turnitin
* If you don't set this property, the integration will fail in sites with titles less than 5 characters, and this error will not be apparent to the end user

##### contentreview.site.max=50
* Defines the maximum number of characters allowed in a site title by Turnitin
* Defaults to "0"
* You **must** set this to 50 for use with Turnitin
* If you don't set this property, the integration will fail in sites with titles greater than 50 characters, and this error will not be apparent to the end user

##### contentreview.assign.min=3
* Defines the minimum number of characters allowed in an assignment title by Turnitin
* Defaults to "0"
* You **must** set this to 3 for use with Turnitin
* If you don't set this property, the integration will fail in sites with assignment titles less than 3 characters, and this error will not be apparent to the end user

##### contentreview.assign.max=100
* Defines the maximum number of characters allowed in an assignment title by Turnitin
* Defaults to "0"
* You **must** set this to 100 for use with Turnitin
* If you don't set this property, the integration will fail in sites with assignment titles greater than 100 characters, and this error will not be apparent to the end user

##### turnitin.enable.assignment2
##### turnitin.apiURL
##### turnitin.secretKey
##### turnitin.said
##### turnitin.aid
##### turnitin.useSourceParameter
##### turnitin.generate.last.name
##### turnitin.option.institution_check
##### turnitin.ltiURL
##### assignment.useContentReview
##### assignment.useContentReviewLTI

## Starting

Once you’re created the LTI tool, go to the turnitin Sakai site and add the tool. You should be able to do add tool. Turnitin should show under "plugins" in the tool list, unless you stealthed it.

Configure turnitin in sakai.properties and restart. See sakai.properties here.

That file includes the values we were using for the old interface. I’m not sure whether you can remove them or not.

## WARNING:

If you have used other code, your database may have a definition for
CONTENTREVIEW_ITEM that will prevent Content Review from being able to
add new assignments. If you have auto.ddl on, Hibernate will add any
necessary fields. But it won't remove fields that shouldn't be there.
The two fields that shouldn't be there but may be are "version" and
"providerId". Either remove them or change the definition is that they
default to null if no value is supplied.
