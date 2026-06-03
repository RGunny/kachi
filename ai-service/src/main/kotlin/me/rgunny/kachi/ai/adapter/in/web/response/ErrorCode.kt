package me.rgunny.kachi.ai.adapter.`in`.web.response

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    LLM_PROVIDER_HEALTH_CHECK_FAILED(HttpStatus.BAD_GATEWAY, "LLM provider 연결 확인에 실패했습니다")
}
