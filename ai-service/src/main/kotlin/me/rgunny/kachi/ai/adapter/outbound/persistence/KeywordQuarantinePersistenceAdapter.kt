package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.application.port.outbound.persistence.KeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.stereotype.Component

/**
 * 키워드 격리 출력 포트의 MongoDB 구현.
 *
 * 대상 종류의 기록 전체를 한 번에 읽어 넘긴다. 격리 대상은 활성 키워드 수를 넘지 않으므로 페이징을 두지 않는다.
 */
@Component
class KeywordQuarantinePersistenceAdapter(
    private val repository: KeywordQuarantineMongoRepository
) : KeywordQuarantinePersistencePort {

    override suspend fun findAllBy(targetType: AiRunTargetType): List<KeywordQuarantine> {
        return repository.findByTargetType(targetType)
            .collectList()
            .awaitSingle()
            .map { it.toDomain() }
    }

    override suspend fun save(quarantine: KeywordQuarantine): KeywordQuarantine {
        return repository.save(KeywordQuarantineMongoDocument.fromDomain(quarantine))
            .awaitSingle()
            .toDomain()
    }
}
