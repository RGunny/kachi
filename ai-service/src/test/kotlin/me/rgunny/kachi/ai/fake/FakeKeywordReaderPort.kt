package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

class FakeKeywordReaderPort : KeywordReaderPort {
    var keywords: List<AiKeyword> = emptyList()
    var readCount: Int = 0

    override suspend fun findActiveKeywords(): List<AiKeyword> {
        readCount += 1
        return keywords
    }
}
