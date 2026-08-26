package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

class FakeKeywordReaderPort : KeywordReaderPort {
    var keywords: List<AiKeyword> = emptyList()
    var readCount: Int = 0

    /** 활성 키워드 조회 실패는 실행 전체를 실패시키므로 별도로 주입한다. */
    var failure: Throwable? = null

    override suspend fun findActiveKeywords(): List<AiKeyword> {
        readCount += 1
        failure?.let { throw it }

        return keywords
    }
}
