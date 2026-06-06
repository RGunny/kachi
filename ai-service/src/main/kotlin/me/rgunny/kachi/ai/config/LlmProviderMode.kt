package me.rgunny.kachi.ai.config

/**
 * enabled LLM provider들을 어떤 방식으로 호출할지 결정하는 실행 모드.
 */
enum class LlmProviderMode(
    val value: String
) {
    /**
     * enabled provider 중 하나를 요청마다 랜덤으로 선택해 호출한다.
     */
    SINGLE_RANDOM("single-random"),

    /**
     * enabled provider 전체를 호출한 뒤 응답을 조합한다.
     */
    AGGREGATE("aggregate");

    companion object {
        fun from(value: String): LlmProviderMode {
            val normalized = value.trim().lowercase()

            return entries.firstOrNull { it.value == normalized }
                ?: error("Unsupported LLM provider mode: $value")
        }
    }
}
