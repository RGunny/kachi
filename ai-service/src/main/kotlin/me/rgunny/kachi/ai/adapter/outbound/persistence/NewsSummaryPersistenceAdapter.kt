package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.outbound.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component

@Component
class NewsSummaryPersistenceAdapter(
    private val repository: NewsSummaryMongoRepository
) : NewsSummaryPersistencePort {

    override suspend fun findByUniqueKey(
        keyword: AiKeyword,
        newsHash: String,
        promptVersion: PromptVersion,
        model: LlmModelName
    ): NewsSummary? {
        return repository.findByKeywordAndNewsHashAndPromptVersionAndModel(
            keyword = keyword.value,
            newsHash = newsHash,
            promptVersion = promptVersion.value,
            model = model.value
        ).awaitSingleOrNull()?.toDomain()
    }

    override suspend fun save(newsSummary: NewsSummary): NewsSummary {
        return repository.save(NewsSummaryMongoDocument.fromDomain(newsSummary))
            .awaitSingle()
            .toDomain()
    }

    override suspend fun saveOrFindExisting(newsSummary: NewsSummary): NewsSummary {
        return try {
            save(newsSummary)
        } catch (error: DuplicateKeyException) {
            // 선조회와 저장 사이에 같은 요약이 저장된 동시 실행은 기존 문서를 반환해 idempotent하게 처리한다.
            findByUniqueKey(
                keyword = newsSummary.keyword,
                newsHash = newsSummary.newsHash,
                promptVersion = newsSummary.promptVersion,
                model = newsSummary.model
            ) ?: throw error
        }
    }
}
