package me.rgunny.kachi.ai.adapter.outbound.persistence

import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * `ai_keyword_quarantines` 접근 repository.
 *
 * 격리 여부와 실패 누적을 한 번에 판단해야 하므로 상태별 조회 없이 대상 종류 단위로만 읽는다.
 */
interface KeywordQuarantineMongoRepository : ReactiveMongoRepository<KeywordQuarantineMongoDocument, UUID> {

    fun findByTargetType(targetType: AiRunTargetType): Flux<KeywordQuarantineMongoDocument>
}
