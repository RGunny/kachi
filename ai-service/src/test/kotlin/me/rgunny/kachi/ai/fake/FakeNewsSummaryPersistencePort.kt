package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.summary.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.summary.NewsSummary

class FakeNewsSummaryPersistencePort : NewsSummaryPersistencePort {
    val savedSummaries: MutableList<NewsSummary> = mutableListOf()
    val savedOutboxes: MutableList<AiOutbox> = mutableListOf()
    val existingSummaries: MutableList<NewsSummary> = mutableListOf()
    var duplicateOnSave: Boolean = false
    var existingAfterDuplicate: NewsSummary? = null
    var saveOrFindExistingCallCount: Int = 0

    override suspend fun findByUniqueKey(
        keyword: AiKeyword,
        newsHash: String,
        promptVersion: PromptVersion
    ): NewsSummary? {
        return existingSummaries.firstOrNull {
            it.keyword == keyword &&
                it.newsHash == newsHash &&
                it.promptVersion == promptVersion
        }
    }

    override suspend fun save(newsSummary: NewsSummary): NewsSummary {
        savedSummaries.add(newsSummary)
        return newsSummary
    }

    override suspend fun saveOrFindExisting(newsSummary: NewsSummary, outbox: AiOutbox): NewsSummary {
        saveOrFindExistingCallCount += 1

        // 중복이면 기존 요약이 이미 이벤트를 남겼으므로 outbox를 저장하지 않는다.
        if (duplicateOnSave) {
            return existingAfterDuplicate ?: newsSummary
        }

        savedOutboxes.add(outbox)

        return save(newsSummary)
    }
}
