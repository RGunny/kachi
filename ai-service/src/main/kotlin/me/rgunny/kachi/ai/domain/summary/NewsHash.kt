package me.rgunny.kachi.ai.domain.summary

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * 뉴스 요약 입력 묶음을 식별하는 hash를 계산한다.
 *
 * 이 hash는 뉴스 제목/본문의 유사도를 판단하지 않는다.
 * 같은 키워드, 같은 조회 기간, 같은 뉴스 id 묶음으로 만든 요약인지 판별하기 위한 중복 저장 방지 키다.
 */
object NewsHash {

    fun calculate(
        keyword: AiKeyword,
        from: Instant?,
        to: Instant?,
        sourceNewsIds: List<UUID>
    ): String {
        require(sourceNewsIds.isNotEmpty()) { "source news ids are required" }

        // 1. 같은 뉴스 묶음은 조회 순서가 달라도 같은 hash가 나오도록 뉴스 id를 정렬하고 중복 제거한다.
        // 자세한 결정 배경은 docs/decisions/011-ai-service-newsHash-요약-중복-방지.md 를 참고한다.
        val source = buildString {
            append(keyword.value)
            append('|')
            append(from?.toString().orEmpty())
            append('|')
            append(to?.toString().orEmpty())
            append('|')
            sourceNewsIds.distinct().sorted().forEach {
                append(it)
                append(',')
            }
        }

        // 2. 긴 뉴스 id 목록을 Mongo unique index에 넣기 좋은 고정 길이 SHA-256 hex 문자열로 변환한다.
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
