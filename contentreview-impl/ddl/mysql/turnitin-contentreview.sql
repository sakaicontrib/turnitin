
    create table CONTENTREVIEW_ITEM (
        id bigint not null auto_increment,
        contentId varchar(255) not null,
        userId varchar(255),
        siteId varchar(255),
        taskId varchar(255),
        externalId varchar(255),
        dateQueued datetime,
        dateSubmitted datetime,
        dateReportReceived datetime,
        status bigint,
        reviewScore integer,
        lastError text,
        retryCount bigint,
        nextRetryTime datetime,
        primary key (id)
    );

    create table CONTENTREVIEW_ACTIVITY_CFG
    (
        ID bigint not null auto_increment,
        TOOL_ID varchar(255) not null,
        ACTIVITY_ID varchar(255) not null,
        PROVIDER_ID bigint not null,
        NAME varchar(255) not null,
        VALUE varchar(2000) not null,
        primary key (ID),
        unique key CFG_ATTRIBUTE (TOOL_ID, ACTIVITY_ID, PROVIDER_ID, NAME)
    );

    create table CONTENTREVIEW_LOCK (
        ID bigint not null auto_increment,
        LAST_MODIFIED datetime not null,
        NAME varchar(255) not null unique,
        HOLDER varchar(255) not null,
        primary key (ID)
    );

    create table CONTENTREVIEW_SYNC_ITEM (
        id bigint not null auto_increment,
        siteId varchar(255) not null,
        dateQueued datetime not null,
        lastTried datetime,
        status integer not null,
        messages text,
        primary key (id)
    );

    create index eval_lock_name on CONTENTREVIEW_LOCK (NAME);
    create index contentreview_content_id on CONTENTREVIEW_ITEM (contentId);
