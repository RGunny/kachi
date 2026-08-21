package me.rgunny.kachi.ai.adapter.inbound.web.response

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    LLM_PROVIDER_HEALTH_CHECK_FAILED(HttpStatus.BAD_GATEWAY, "LLM provider 연결 확인에 실패했습니다"),
    INVALID_KEYWORD_EXPANSION_REQUEST(HttpStatus.BAD_REQUEST, "키워드 확장 요청이 올바르지 않습니다"),
    KEYWORD_EXPANSION_ALREADY_RUNNING(HttpStatus.CONFLICT, "키워드 확장이 이미 실행 중입니다"),
    INVALID_NEWS_SUMMARY_REQUEST(HttpStatus.BAD_REQUEST, "뉴스 요약 요청이 올바르지 않습니다"),
    NEWS_SUMMARY_ALREADY_RUNNING(HttpStatus.CONFLICT, "뉴스 요약이 이미 실행 중입니다")
}
