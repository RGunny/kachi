package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.out.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.summary.NewsSummary

class FakeNewsSummaryPersistencePort : NewsSummaryPersistencePort {
    val savedSummaries: MutableList<NewsSummary> = mutableListOf()
    val existingSummaries: MutableList<NewsSummary> = mutableListOf()
    var duplicateOnSave: Boolean = false
    var existingAfterDuplicate: NewsSummary? = null
    var saveOrFindExistingCallCount: Int = 0

    override suspend fun findByUniqueKey(
        keyword: AiKeyword,
        newsHash: String,
        promptVersion: PromptVersion,
        model: LlmModelName
    ): NewsSummary? {
        return existingSummaries.firstOrNull {
            it.keyword == keyword &&
                it.newsHash == newsHash &&
                it.promptVersion == promptVersion &&
                it.model == model
        }
    }

    override suspend fun save(newsSummary: NewsSummary): NewsSummary {
        savedSummaries.add(newsSummary)
        return newsSummary
    }

    override suspend fun saveOrFindExisting(newsSummary: NewsSummary): NewsSummary {
        saveOrFindExistingCallCount += 1

        if (duplicateOnSave) {
            return existingAfterDuplicate ?: newsSummary
        }

        return save(newsSummary)
    }
}
