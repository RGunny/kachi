package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.outbound.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Component
class NewsSummaryPersistenceAdapter(
    private val repository: NewsSummaryMongoRepository,
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
) : NewsSummaryPersistencePort {

    override suspend fun findByUniqueKey(
        keyword: AiKeyword,
        newsHash: String,
        promptVersion: PromptVersion
    ): NewsSummary? {
        return repository.findByKeywordAndNewsHashAndPromptVersion(
            keyword = keyword.value,
            newsHash = newsHash,
            promptVersion = promptVersion.value
        ).awaitSingleOrNull()?.toDomain()
    }

    override suspend fun save(newsSummary: NewsSummary): NewsSummary {
        return repository.save(NewsSummaryMongoDocument.fromDomain(newsSummary))
            .awaitSingle()
            .toDomain()
    }

    override suspend fun saveOrFindExisting(newsSummary: NewsSummary, outbox: AiOutbox): NewsSummary {
        return try {
            transactionalOperator.executeAndAwait {
                mongoTemplate.insert(NewsSummaryMongoDocument.fromDomain(newsSummary)).awaitSingle()
                mongoTemplate.insert(AiOutboxMongoDocument.fromDomain(outbox)).awaitSingle()
            }

            newsSummary
        } catch (error: DuplicateKeyException) {
            // 이 catch에는 요약 unique와 outbox eventKey unique 충돌이 함께 들어온다.
            // 선조회와 저장 사이에 같은 요약이 저장된 경우만 기존 문서를 돌려주고, 그 외에는 그대로 던진다.
            findByUniqueKey(
                keyword = newsSummary.keyword,
                newsHash = newsSummary.newsHash,
                promptVersion = newsSummary.promptVersion
            ) ?: throw error
        }
    }
}
