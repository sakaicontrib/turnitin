-- Add required new columns
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN urlAccessed bool NOT NULL default false;
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN submissionId VARCHAR(255);
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN resubmission bool NOT NULL default false;
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN externalGrade VARCHAR(255);

-- Drop unnecessary columns
ALTER TABLE CONTENTREVIEW_ITEM DROP version, DROP providerId;

-- Close off HTTP access to submissions made prior to the deployment date of the LTI code  (TII-240)
UPDATE CONTENTREVIEW_ITEM SET urlAccessed = 1 WHERE dateSubmitted < SYSDATE();

-- Check if LTI_CONTENT is semicolon or new line delimited and apply one of the following as appropriate (TII-243):
UPDATE LTI_CONTENT SET CUSTOM = REPLACE(CUSTOM, ';custom_', ';') WHERE tool_id in (select id from lti_tools where SITE_ID in ('!turnitin', '!turnitin_reports'));
-- OR --
UPDATE LTI_CONTENT SET CUSTOM = REPLACE(CUSTOM, '\ncustom_', '\n') WHERE tool_id in (select id from lti_tools where SITE_ID in ('!turnitin', '!turnitin_reports'));
