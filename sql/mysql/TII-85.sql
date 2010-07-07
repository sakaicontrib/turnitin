create table CONTENTREVIEW_SYNC_ITEM (
        id bigint not null auto_increment,
        siteId varchar(255) not null,
        dateQueued datetime not null,
        lastTried datetime,
        status integer not null,
        messages text,
        primary key (id)
    );
