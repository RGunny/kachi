package me.rgunny.kachi.ai.application.port.outbound.persistence

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
     * 격리 기록을 저장한다. 기존 기록의 갱신은 새 기록으로 쌓이지 않고 그 기록을 대체한다.
     */
    suspend fun save(quarantine: KeywordQuarantine): KeywordQuarantine
}
