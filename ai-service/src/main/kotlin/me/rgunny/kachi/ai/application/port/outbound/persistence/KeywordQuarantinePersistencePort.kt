package me.rgunny.kachi.ai.application.port.outbound.persistence

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.run.AiRunTargetType

/**
 * 키워드 격리 기록 저장소 출력 포트
 *
 * targetType + keyword마다 기록은 한 건이다. 연속 실패 횟수가 실행을 넘겨가며 누적되려면
 * 같은 키워드의 실패가 늘 같은 기록에 쌓여야 한다.
 */
interface KeywordQuarantinePersistencePort {

    /**
     * 대상 종류의 격리 기록을 모두 읽는다.
     *
     * 실행마다 대상 제외와 실패 누적에 같은 기록이 함께 필요하므로 격리된 것만 따로 조회하지 않는다.
     * 실패한 적 없는 키워드는 기록 자체가 없다. 결과에 없다는 것은 연속 실패 0회를 뜻한다.
     */
    suspend fun findAllBy(targetType: AiRunTargetType): List<KeywordQuarantine>

    /**
     * 대상 종류의 특정 키워드 기록을 읽는다. null은 실패한 적 없는 키워드다.
     */
    suspend fun findBy(targetType: AiRunTargetType, keyword: AiKeyword): KeywordQuarantine?

    /**
     * 격리 기록을 저장한다. 기존 기록의 갱신은 새 기록으로 쌓이지 않고 그 기록을 대체한다.
     */
    suspend fun save(quarantine: KeywordQuarantine): KeywordQuarantine

    /**
     * 격리 전이와 발행 대기 이벤트를 한 트랜잭션으로 저장한다.
     *
     * 격리됐는데 알림이 없는 상태를 만들지 않기 위해 전이 저장과 이벤트 기록의 결과를 묶는다.
     */
    suspend fun saveQuarantined(quarantine: KeywordQuarantine, outbox: AiOutbox): KeywordQuarantine
}
