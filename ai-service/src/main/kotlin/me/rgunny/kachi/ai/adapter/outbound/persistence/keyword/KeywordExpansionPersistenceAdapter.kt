package me.rgunny.kachi.ai.adapter.outbound.persistence.keyword

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.outbound.keyword.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component

@Component
class KeywordExpansionPersistenceAdapter(
    private val repository: KeywordExpansionMongoRepository
) : KeywordExpansionPersistencePort {

    override suspend fun findByUniqueKey(
        keyword: AiKeyword,
        promptVersion: PromptVersion
    ): KeywordExpansion? {
        return repository.findByKeywordAndPromptVersion(
            keyword = keyword.value,
            promptVersion = promptVersion.value
        ).awaitSingleOrNull()?.toDomain()
    }

    override suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion {
        return repository.save(KeywordExpansionMongoDocument.fromDomain(keywordExpansion))
            .awaitSingle()
            .toDomain()
    }

    override suspend fun saveOrFindExisting(keywordExpansion: KeywordExpansion): KeywordExpansion {
        return try {
            save(keywordExpansion)
        } catch (error: DuplicateKeyException) {
            // 선조회와 저장 사이에 같은 확장이 저장된 동시 실행은 기존 문서를 반환해 idempotent하게 처리한다.
            findByUniqueKey(
                keyword = keywordExpansion.keyword,
                promptVersion = keywordExpansion.promptVersion
            ) ?: throw error
        }
    }
}
