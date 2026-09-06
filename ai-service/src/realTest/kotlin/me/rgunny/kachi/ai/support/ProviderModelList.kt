package me.rgunny.kachi.ai.support

/**
 * OpenAI 규격 `GET /models` 응답. 필요한 것은 모델 id뿐이다.
 */
data class ProviderModelList(
    val data: List<ProviderModelEntry> = emptyList()
)

data class ProviderModelEntry(
    val id: String
)
