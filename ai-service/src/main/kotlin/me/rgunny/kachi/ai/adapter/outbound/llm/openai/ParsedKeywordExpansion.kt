package me.rgunny.kachi.ai.adapter.outbound.llm.openai

/**
 * LLM이 반환한 키워드 확장 JSON을 그대로 받는 역직렬화 전용 타입.
 *
 * provider가 필드를 빠뜨려도 파싱 자체는 실패하지 않도록 기본값을 두고, 비어 있는지는 [OpenAiChatAdapter]가 본다.
 */
data class ParsedKeywordExpansion(
    val keywords: List<String> = emptyList()
)
