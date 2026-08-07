package me.rgunny.kachi.collector.adapter.inbound.web.response

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    COLLECTION_ALREADY_RUNNING(HttpStatus.CONFLICT, "뉴스 수집이 이미 실행 중입니다"),
    INVALID_NEWS_QUERY(HttpStatus.BAD_REQUEST, "뉴스 조회 요청이 올바르지 않습니다"),
    INVALID_NEWS_SOURCE(HttpStatus.BAD_REQUEST, "지원하지 않는 뉴스 provider입니다"),
    NEWS_PROVIDER_NOT_ENABLED(HttpStatus.NOT_FOUND, "활성화된 뉴스 provider가 없습니다")
}
