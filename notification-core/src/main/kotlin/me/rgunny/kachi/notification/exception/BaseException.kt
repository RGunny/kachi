package me.rgunny.kachi.notification.exception

abstract class BaseException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * 로그와 운영 화면에서 원인을 좁히기 위한 최소 식별자.
     */
    open val context: Map<String, String> = emptyMap()
}
