-- Add required new columns
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN urlAccessed bool NOT NULL default false;
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN submissionId VARCHAR(255);
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN resubmission bool NOT NULL default false;
ALTER TABLE CONTENTREVIEW_ITEM ADD COLUMN externalGrade VARCHAR(255);

-- Drop unnecessary columns
ALTER TABLE CONTENTREVIEW_ITEM DROP COLUMN version, providerId;

-- Close off HTTP access to submissions made prior to the deployment date of the LTI code (TII-240)
UPDATE CONTENTREVIEW_ITEM SET urlAccessed = 1 WHERE dateSubmitted < SYSDATE;
