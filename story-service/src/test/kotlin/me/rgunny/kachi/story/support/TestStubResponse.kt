package me.rgunny.kachi.story.support

import java.time.Duration

/** [TestStubServer]가 한 요청에 돌려줄 HTTP 응답. */
data class TestStubResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
    /** 응답을 보내기 전에 기다리는 시간. */
    val delay: Duration = Duration.ZERO
)
