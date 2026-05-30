package me.rgunny.kachi.collector.adapter.`in`.web.response

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    COLLECTION_ALREADY_RUNNING(HttpStatus.CONFLICT, "뉴스 수집이 이미 실행 중입니다")
}
