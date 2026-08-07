package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListActiveKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.ListActiveKeywordsUseCase
import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import org.springframework.stereotype.Service

@Service
class ActiveKeywordQueryService(
    private val keywordPersistencePort: KeywordPersistencePort
) : ListActiveKeywordsUseCase {

    override fun listActiveKeywords(): List<ListActiveKeywordResult> {
        // 1. 사용자들이 등록한 enabled=true 키워드를 모두 조회한다.
        val activeKeywords = keywordPersistencePort.findAllEnabled()

        // 2. 여러 사용자가 같은 키워드를 등록할 수 있으므로 수집 대상은 이름 기준으로 중복 제거한다.
        return activeKeywords
            .map { it.name.value }
            .distinct()
            .sorted()
            .map { ListActiveKeywordResult(name = it) }
    }
}
