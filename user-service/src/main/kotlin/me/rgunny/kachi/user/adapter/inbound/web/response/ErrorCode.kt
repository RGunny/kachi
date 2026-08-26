package me.rgunny.kachi.user.adapter.inbound.web.response

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    INACTIVE_USER(HttpStatus.FORBIDDEN, "활성 사용자가 아닙니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    SUBSCRIPTION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "구독에 접근할 수 없습니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    DUPLICATE_SUBSCRIPTION(HttpStatus.CONFLICT, "이미 구독 중인 키워드입니다"),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독을 찾을 수 없습니다"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다")
}
