package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * chat completions 요청 본문.
 *
 * 값이 없는 필드는 싣지 않는다. provider마다 모르는 필드를 거부하는 정도가 달라, 쓰지 않는 필드는 아예 보내지 않는 것이 안전하다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double = 0.2,
    val max_tokens: Int = 512,
    val response_format: OpenAiResponseFormat? = null,
    val reasoning_effort: String? = null,
    val thinking: OpenAiThinking? = null
)

data class OpenAiChatMessage(
    val role: String,
    val content: String
)

/**
 * 응답 형식 지정. `json_object`는 content가 JSON 하나임을 provider에 요구한다.
 */
data class OpenAiResponseFormat(
    val type: String
) {
    companion object {
        val JSON_OBJECT = OpenAiResponseFormat(type = "json_object")
    }
}

/**
 * 사고(thinking) 켜기·끄기. 이 규격에서 사고를 다루는 provider는 `{"type": "enabled"|"disabled"}`로 받는다.
 */
data class OpenAiThinking(
    val type: String
)
