
    create table CONTENTREVIEW_ITEM (
        id number(19,0) not null,
        contentId varchar2(255) not null,
        userId varchar2(255),
        siteId varchar2(255),
        taskId varchar2(255),
        externalId varchar2(255),
        dateQueued date,
        dateSubmitted date,
        dateReportReceived date,
        status number(19,0),
        reviewScore number(10,0),
        lastError clob,
        retryCount number(19,0),
        nextRetryTime date,
        primary key (id)
    );

    create table CONTENTREVIEW_LOCK (
        ID number(19,0) not null,
        LAST_MODIFIED date not null,
        NAME varchar2(255) not null unique,
        HOLDER varchar2(255) not null,
        primary key (ID)
    );

    create table CONTENTREVIEW_SYNC_ITEM (
        id number(19,0) not null,
        siteId varchar2(255) not null,
        dateQueued date not null,
        lastTried date,
        status number(10,0) not null,
        messages clob,
        primary key (id)
    );

    create index eval_lock_name on CONTENTREVIEW_LOCK (NAME);

    create sequence TURNITIN_ROSTER_SYNC_ITEM_ID_SEQ;

    create sequence contentreview_ITEM_ID_SEQ;

    create sequence hibernate_sequence;
