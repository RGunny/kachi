package me.rgunny.kachi.ai.support

import java.time.Duration

/**
 * [TestStubServer]가 한 요청에 돌려줄 HTTP 응답.
 */
data class TestStubResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
    /** 응답을 보내기 전에 기다리는 시간. 호출하는 쪽의 timeout이 실제로 걸리는지 볼 때 쓴다. */
    val delay: Duration = Duration.ZERO
)
