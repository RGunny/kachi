package me.rgunny.kachi.e2e.support

/**
 * [TestStubServer]가 한 요청에 돌려줄 HTTP 응답.
 */
data class TestStubResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String
)
