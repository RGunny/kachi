package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.application.port.outbound.persistence.KeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * 키워드 격리 출력 포트의 MongoDB 구현.
 *
 * 대상 종류의 기록 전체를 한 번에 읽어 넘긴다. 격리 대상은 활성 키워드 수를 넘지 않으므로 페이징을 두지 않는다.
 */
@Component
class KeywordQuarantinePersistenceAdapter(
    private val repository: KeywordQuarantineMongoRepository,
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
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

    /**
     * 격리 기록은 첫 저장일 수도 기존 기록의 갱신일 수도 있어 save로, outbox는 늘 새 행이라 insert로 쓴다.
     *
     * 반환값은 저장 결과가 아니라 인자다. 조건부 갱신이 아니라서 저장된 문서와 넘어온 값이 같다.
     * 이벤트 키가 이미 있으면 insert가 실패하고 격리 전이도 함께 되돌아간다.
     */
    override suspend fun saveQuarantined(
        quarantine: KeywordQuarantine,
        outbox: AiOutbox
    ): KeywordQuarantine {
        transactionalOperator.executeAndAwait {
            mongoTemplate.save(KeywordQuarantineMongoDocument.fromDomain(quarantine)).awaitSingle()
            mongoTemplate.insert(AiOutboxMongoDocument.fromDomain(outbox)).awaitSingle()
        }

        return quarantine
    }
}
