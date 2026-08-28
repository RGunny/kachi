package me.rgunny.kachi.notification.application.port.outbound.recipient.model

/**
 * 수신 주소를 쓸 수 없는 사유. 일시 장애가 아니라 수신자 쪽 상태이므로 이번 발송은 재시도하지 않고 끝낸다.
 * PENDING·NOT_FOUND는 나중에 바인딩이 생기면 달라질 수 있지만, 그때는 새 알림이 그 주소로 간다.
 */
enum class RecipientUnavailableReason {
    /** 그 수신자·채널의 바인딩이 없다. 수신자 식별자 형식이 잘못된 경우도 같다. */
    NOT_FOUND,

    /** 바인딩이 아직 연결되지 않았다. */
    PENDING,

    /** 바인딩이 해지됐다. */
    REVOKED,

    /** 바인딩은 활성인데 주소가 비어 있다. 원천의 계약 위반에 대한 방어다. */
    ADDRESS_MISSING,

    /** 조회한 바인딩의 채널이 알림의 채널과 다르다. 원천의 계약 위반에 대한 방어다. */
    CHANNEL_MISMATCH,
}
