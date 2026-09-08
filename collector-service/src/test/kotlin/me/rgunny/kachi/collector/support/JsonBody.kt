package me.rgunny.kachi.collector.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 응답 body를 JSON 트리로 읽는다.
 *
 * 내부 API 응답은 성공/실패 구조가 갈리므로 타입 하나로 역직렬화하기 어렵다.
 * 트리로 읽어 필요한 필드만 단언한다.
 */
object JsonBody {
    private val mapper = JsonMapper.builder().build()

    fun parse(body: String?): JsonNode {
        return mapper.readTree(requireNotNull(body) { "응답 body가 비어 있습니다" })
    }
}
