
    create table CONTENTREVIEW_ITEM (
        id int8 not null,
        contentId varchar(255) not null,
        userId varchar(255),
        siteId varchar(255),
        taskId varchar(255),
        externalId varchar(255),
        dateQueued timestamp,
        dateSubmitted timestamp,
        dateReportReceived timestamp,
        status int8,
        reviewScore int4,
        lastError text,
        retryCount int8,
        nextRetryTime timestamp,
        primary key (id)
    );

    create table CONTENTREVIEW_LOCK (
        ID int8 not null,
        LAST_MODIFIED timestamp not null,
        NAME varchar(255) not null unique,
        HOLDER varchar(255) not null,
        primary key (ID)
    );

    create table CONTENTREVIEW_SYNC_ITEM (
        id int8 not null,
        siteId varchar(255) not null,
        dateQueued timestamp not null,
        lastTried timestamp,
        status int4 not null,
        messages text,
        primary key (id)
    );

    create index eval_lock_name on CONTENTREVIEW_LOCK (NAME);

    create sequence TII_ROSTER_SYNC_ITEM_ID_SEQ;

    create sequence contentreview_ITEM_ID_SEQ;

    create sequence hibernate_sequence;
