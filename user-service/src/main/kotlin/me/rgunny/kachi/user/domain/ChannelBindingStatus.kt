package me.rgunny.kachi.user.domain

/**
 * 바인딩 생명주기. 주소는 ACTIVE일 때만 있다.
 */
enum class ChannelBindingStatus {
    /** 연결 토큰을 발급했고 주소가 아직 없다. */
    PENDING,

    /** 주소가 있고 발송할 수 있다. */
    ACTIVE,

    /** 사용자가 해지했다. 주소는 지워져 있다. */
    REVOKED
}
