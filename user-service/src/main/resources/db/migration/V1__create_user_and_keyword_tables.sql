create table users (
    id binary(16) not null,
    email varchar(255) not null,
    nickname varchar(100) not null,
    status enum ('ACTIVE', 'DELETED', 'INACTIVE') not null,
    role enum ('ADMIN', 'USER') not null,
    auth_provider enum ('GOOGLE', 'KAKAO', 'LOCAL', 'NAVER') not null,
    provider_user_id varchar(255),
    registered_at datetime(6) not null,
    last_login_at datetime(6),
    deactivated_at datetime(6),
    primary key (id),
    constraint uk_users_email unique (email),
    constraint uk_users_auth_provider_provider_user_id unique (auth_provider, provider_user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table keywords (
    id binary(16) not null,
    user_id binary(16) not null,
    name varchar(100) not null,
    enabled bit not null,
    registered_at datetime(6) not null,
    disabled_at datetime(6),
    primary key (id),
    constraint uk_keywords_user_id_name unique (user_id, name)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
