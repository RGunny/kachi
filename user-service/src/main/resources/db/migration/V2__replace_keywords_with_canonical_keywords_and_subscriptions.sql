drop table keywords;

create table keywords (
    id            binary(16)   not null,
    canonical_key varchar(100) collate utf8mb4_bin not null,
    display_name  varchar(100) not null,
    created_at    datetime(6)  not null,
    primary key (id),
    constraint uk_keywords_canonical_key unique (canonical_key)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table subscriptions (
    id            binary(16)  not null,
    user_id       binary(16)  not null,
    keyword_id    binary(16)  not null,
    enabled       bit         not null,
    registered_at datetime(6) not null,
    disabled_at   datetime(6),
    primary key (id),
    constraint uk_subscriptions_user_id_keyword_id unique (user_id, keyword_id),
    constraint fk_subscriptions_keyword foreign key (keyword_id) references keywords (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index ix_subscriptions_keyword_id_enabled on subscriptions (keyword_id, enabled);

create table subscription_channels (
    subscription_id binary(16) not null,
    channel         enum ('SLACK', 'DISCORD', 'TELEGRAM') not null,
    primary key (subscription_id, channel),
    constraint fk_subscription_channels_subscription foreign key (subscription_id) references subscriptions (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
