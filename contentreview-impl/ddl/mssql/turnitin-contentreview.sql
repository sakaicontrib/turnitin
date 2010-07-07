
    create table CONTENTREVIEW_ITEM (
        id numeric(19,0) identity not null,
        contentId varchar(255) not null,
        userId varchar(255) null,
        siteId varchar(255) null,
        taskId varchar(255) null,
        externalId varchar(255) null,
        dateQueued datetime null,
        dateSubmitted datetime null,
        dateReportReceived datetime null,
        status numeric(19,0) null,
        reviewScore int null,
        lastError text null,
        retryCount numeric(19,0) null,
        nextRetryTime datetime null,
        primary key (id)
    );

    create table CONTENTREVIEW_LOCK (
        ID numeric(19,0) identity not null,
        LAST_MODIFIED datetime not null,
        NAME varchar(255) not null unique,
        HOLDER varchar(255) not null,
        primary key (ID)
    );

    create table CONTENTREVIEW_SYNC_ITEM (
        id numeric(19,0) identity not null,
        siteId varchar(255) not null,
        dateQueued datetime not null,
        lastTried datetime null,
        status int not null,
        messages text null,
        primary key (id)
    );

    create index eval_lock_name on CONTENTREVIEW_LOCK (NAME);
