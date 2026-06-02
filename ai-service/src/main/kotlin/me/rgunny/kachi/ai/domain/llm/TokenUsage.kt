package me.rgunny.kachi.ai.domain.llm

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
) {
    init {
        require(inputTokens >= 0) { "입력 token 수는 0 이상이어야 합니다" }
        require(outputTokens >= 0) { "출력 token 수는 0 이상이어야 합니다" }
    }

    val totalTokens: Int = inputTokens + outputTokens
}
