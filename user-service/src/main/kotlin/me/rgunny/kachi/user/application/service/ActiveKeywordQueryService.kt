package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.keyword.ListActiveKeywordsUseCase
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListActiveKeywordResult
import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import org.springframework.stereotype.Service

/**
 * 수집·요약 대상 키워드 조회.
 *
 * enabled 구독이 하나 이상인 canonical 키워드만 내보낸다. 같은 키워드를 여러 사용자가 구독해도 한 건이다.
 */
@Service
class ActiveKeywordQueryService(
    private val keywordPersistencePort: KeywordPersistencePort
) : ListActiveKeywordsUseCase {

    override fun listActiveKeywords(): List<ListActiveKeywordResult> {
        return keywordPersistencePort.findAllWithEnabledSubscription()
            .sortedBy { it.canonicalKey.value }
            .map(ListActiveKeywordResult::from)
    }
}
