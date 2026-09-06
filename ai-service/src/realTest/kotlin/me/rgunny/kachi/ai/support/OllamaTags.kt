package me.rgunny.kachi.ai.support

/**
 * Ollama 고유 `GET /api/tags` 응답. tag는 이동 alias라 어느 가중치가 답했는지는 digest로만 남는다.
 */
data class OllamaTags(
    val models: List<OllamaTag> = emptyList()
)

data class OllamaTag(
    val name: String,
    val digest: String
)
