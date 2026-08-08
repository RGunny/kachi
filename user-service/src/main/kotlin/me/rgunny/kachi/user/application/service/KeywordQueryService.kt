package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordsQuery
import me.rgunny.kachi.user.application.port.inbound.keyword.ListKeywordsUseCase
import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import org.springframework.stereotype.Service

@Service
class KeywordQueryService(
    private val keywordPersistencePort: KeywordPersistencePort,
    private val activeUserValidator: ActiveUserValidator
) : ListKeywordsUseCase {

    override fun list(query: ListKeywordsQuery): List<ListKeywordResult> {
        activeUserValidator.get(query.userId)

        return keywordPersistencePort.findAllByUserId(query.userId)
            .sortedByDescending { it.registeredAt }
            .map(ListKeywordResult::from)
    }
}
