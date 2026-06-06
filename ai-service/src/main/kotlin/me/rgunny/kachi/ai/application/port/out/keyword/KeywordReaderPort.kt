package me.rgunny.kachi.ai.application.port.out.keyword

import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 외부 키워드 소유 서비스에서 활성 키워드를 읽는 출력 포트
 */
interface KeywordReaderPort {

    suspend fun findActiveKeywords(): List<AiKeyword>
}
