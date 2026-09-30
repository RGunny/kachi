package me.rgunny.kachi.ai.application.service.quarantine

import me.rgunny.kachi.ai.application.port.inbound.quarantine.FindKeywordQuarantinesUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesQuery
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesResult
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.KeywordQuarantineSummary
import me.rgunny.kachi.ai.application.port.outbound.quarantine.KeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.stereotype.Service

/**
 * 격리 기록을 조건에 맞게 읽는 조회 유스케이스.
 *
 * 상태 필터는 저장소가 아니라 여기서 건다.
 * 기록 수가 활성 키워드 수를 넘지 않아 전량을 읽어도 되고, 저장소는 대상 제외 판단에 쓰는 대상 종류 단위 조회 하나만 유지한다.
 */
@Service
class FindKeywordQuarantinesService(
    private val keywordQuarantinePersistencePort: KeywordQuarantinePersistencePort
) : FindKeywordQuarantinesUseCase {

    override suspend fun find(query: FindKeywordQuarantinesQuery): FindKeywordQuarantinesResult {
        // 1. 대상 종류를 지정하지 않은 조회는 두 종류를 모두 읽어 합친다.
        val targetTypes = query.targetType?.let { listOf(it) } ?: AiRunTargetType.entries

        val quarantines = targetTypes
            .flatMap { keywordQuarantinePersistencePort.findAllBy(it) }
            .filter { query.status == null || it.status == query.status }
            .map(KeywordQuarantineSummary::from)

        return FindKeywordQuarantinesResult(quarantines)
    }
}
