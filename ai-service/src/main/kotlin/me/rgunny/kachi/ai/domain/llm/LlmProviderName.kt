package me.rgunny.kachi.ai.domain.llm

@JvmInline
value class LlmProviderName private constructor(
    val value: String
) {
    companion object {

        /**
         * 어느 provider도 호출되지 않았음을 나타내는 이름.
         *
         * 전 provider가 차단돼 요청이 나가지 않은 실패에 쓴다. 특정 provider가 실패했다고 기록하면 거짓이 된다.
         */
        val NONE: LlmProviderName = LlmProviderName("none")

        fun of(value: String): LlmProviderName {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "LLM provider 이름은 빈 값일 수 없습니다" }

            return LlmProviderName(normalized)
        }
    }
}
