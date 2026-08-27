package me.rgunny.kachi.notification.exception.routing

/**
 * 구독자 조회 실패. 어느 코드든 같은 요청을 다시 보내면 결과가 달라질 수 있으므로 호출자가 재시도 여부를 정한다.
 */
class SubscriberReaderException(
    errorCode: RoutingErrorCode,
    detail: String,
    cause: Throwable? = null,
) : RoutingException(
    errorCode = errorCode,
    message = "${errorCode.message} ($detail)",
    cause = cause,
)
