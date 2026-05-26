-- Final UUID index performance experiment schema reference.
-- scripts/run.sh creates this shape in each independent MySQL container.
--
-- Common columns:
--   service_type varchar(20) not null
--   created_at   datetime(6) not null
--   payload      varchar(100) not null
--
-- Common indexes:
--   primary key (id)
--   key idx_records_created_at (created_at desc)
--   key idx_records_service_id (service_type, id desc)

-- bigint
create table records (
    id bigint not null auto_increment,
    service_type varchar(20) not null,
    created_at datetime(6) not null,
    payload varchar(100) not null,
    primary key (id),
    key idx_records_created_at (created_at desc),
    key idx_records_service_id (service_type, id desc)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- bin16_uuid_v7
create table records (
    id binary(16) not null,
    service_type varchar(20) not null,
    created_at datetime(6) not null,
    payload varchar(100) not null,
    primary key (id),
    key idx_records_created_at (created_at desc),
    key idx_records_service_id (service_type, id desc)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- char36_uuid_v7
create table records (
    id char(36) not null,
    service_type varchar(20) not null,
    created_at datetime(6) not null,
    payload varchar(100) not null,
    primary key (id),
    key idx_records_created_at (created_at desc),
    key idx_records_service_id (service_type, id desc)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- prefix_id
create table records (
    id varchar(64) not null,
    service_type varchar(20) not null,
    created_at datetime(6) not null,
    payload varchar(100) not null,
    primary key (id),
    key idx_records_created_at (created_at desc),
    key idx_records_service_id (service_type, id desc)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
