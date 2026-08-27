create table channel_bindings (
    id                    binary(16)   not null,
    user_id               binary(16)   not null,
    channel               enum ('SLACK', 'DISCORD', 'TELEGRAM') not null,
    status                enum ('PENDING', 'ACTIVE', 'REVOKED') not null,
    address_ciphertext    varbinary(1024),
    key_version           int          not null,
    link_token_hash       binary(32),
    link_token_expires_at datetime(6),
    created_at            datetime(6)  not null,
    bound_at              datetime(6),
    revoked_at            datetime(6),
    primary key (id),
    constraint uk_channel_bindings_user_id_channel unique (user_id, channel),
    constraint uk_channel_bindings_link_token_hash unique (link_token_hash)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
