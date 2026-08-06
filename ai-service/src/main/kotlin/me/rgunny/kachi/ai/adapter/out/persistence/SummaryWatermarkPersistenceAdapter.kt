package me.rgunny.kachi.ai.adapter.out.persistence

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.out.persistence.SummaryWatermarkPersistencePort
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark
import org.springframework.stereotype.Component

/**
 * watermark 출력 포트의 MongoDB 구현.
 *
 * "대상 종류당 한 건" 계약을 `_id = targetType`으로 지킨다. 최초 저장과 이후 전진이 모두 같은 문서를 덮어쓴다.
 */
@Component
class SummaryWatermarkPersistenceAdapter(
    private val repository: SummaryWatermarkMongoRepository
) : SummaryWatermarkPersistencePort {

    override suspend fun findBy(targetType: AiRunTargetType): SummaryWatermark? {
        return repository.findById(targetType.name)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun save(watermark: SummaryWatermark): SummaryWatermark {
        return repository.save(SummaryWatermarkMongoDocument.fromDomain(watermark))
            .awaitSingle()
            .toDomain()
    }
}
