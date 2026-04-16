create table notification
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                                                        not null,
    category    enum ('NOTICE', 'MESSAGE', 'TODO')                            not null,
    title       varchar(128)                                                  not null,
    content     varchar(500)                                                  not null,
    read_flag   tinyint(1)                      default 0                      not null,
    route       varchar(255)                                                  null,
    route_query text                                                          null,
    extra       varchar(255)                                                  null,
    status      varchar(16)                                                   null,
    created_at  datetime                        default CURRENT_TIMESTAMP      not null,
    read_at     datetime                                                      null,
    constraint fk_notification_user
        foreign key (user_id) references sys_user (id)
            on delete cascade
);

create index idx_notification_user_created
    on notification (user_id, created_at);

create index idx_notification_user_read_category
    on notification (user_id, read_flag, category);
