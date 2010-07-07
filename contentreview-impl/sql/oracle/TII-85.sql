create table CONTENTREVIEW_SYNC_ITEM (
        id number(19,0) not null,
        siteId varchar2(255) not null,
        dateQueued date not null,
        lastTried date,
        status number(10,0) not null,
        messages clob,
        primary key (id)
    );

create sequence TII_ROSTER_SYNC_ITEM_ID_SEQ;
