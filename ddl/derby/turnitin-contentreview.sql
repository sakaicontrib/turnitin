
    create table CONTENTREVIEW_ITEM (
        id bigint not null,
        contentId varchar(255) not null,
        userId varchar(255),
        siteId varchar(255),
        taskId varchar(255),
        externalId varchar(255),
        dateQueued timestamp,
        dateSubmitted timestamp,
        dateReportReceived timestamp,
        status bigint,
        reviewScore integer,
        lastError clob(255),
        retryCount bigint,
        nextRetryTime timestamp,
        primary key (id)
    );

    create table CONTENTREVIEW_LOCK (
        ID bigint not null,
        LAST_MODIFIED timestamp not null,
        NAME varchar(255) not null unique,
        HOLDER varchar(255) not null,
        primary key (ID)
    );

    create table CONTENTREVIEW_SYNC_ITEM (
        id bigint not null,
        siteId varchar(255) not null,
        dateQueued timestamp not null,
        lastTried timestamp,
        status integer not null,
        messages clob(255),
        primary key (id)
    );

    create index eval_lock_name on CONTENTREVIEW_LOCK (NAME);

    create table hibernate_unique_key (
         next_hi integer 
    );

    insert into hibernate_unique_key values ( 0 );
