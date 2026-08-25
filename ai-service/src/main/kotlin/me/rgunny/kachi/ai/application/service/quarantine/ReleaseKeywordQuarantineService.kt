package me.rgunny.kachi.ai.application.service.quarantine

import me.rgunny.kachi.ai.application.exception.KeywordQuarantineNotFoundException
import me.rgunny.kachi.ai.application.exception.KeywordQuarantineNotReleasableException
import me.rgunny.kachi.ai.application.port.inbound.quarantine.ReleaseKeywordQuarantineUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.KeywordQuarantineSummary
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineCommand
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineResult
import me.rgunny.kachi.ai.application.port.outbound.persistence.KeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * 격리된 키워드를 다시 실행 대상으로 되돌린다.
 *
 * 해제 가능 여부를 먼저 확인해 API 응답 규약대로 실패를 나눈다. 도메인의 같은 확인은 최후 방어로 남는다.
 */
@Service
class ReleaseKeywordQuarantineService(
    private val keywordQuarantinePersistencePort: KeywordQuarantinePersistencePort,
    private val clock: Clock
) : ReleaseKeywordQuarantineUseCase {

    override suspend fun release(command: ReleaseKeywordQuarantineCommand): ReleaseKeywordQuarantineResult {
        // 1. 실패한 적 없는 키워드는 기록 자체가 없다. 해제할 대상이 없다.
        val quarantine = keywordQuarantinePersistencePort.findBy(command.targetType, command.keyword)
            ?: throw KeywordQuarantineNotFoundException(command.targetType, command.keyword)

        // 2. 추적 중이거나 이미 해제된 기록은 해제 대상이 아니다.
        if (!quarantine.isQuarantined) {
            throw KeywordQuarantineNotReleasableException(command.keyword, quarantine.status)
        }

        val now = Instant.now(clock)
        logRelease(quarantine)

        // 3. 연속 실패 누적은 도메인이 0으로 되돌린다. 다음 실행부터 다시 대상에 들어온다.
        val released = keywordQuarantinePersistencePort.save(quarantine.release(now))

        return ReleaseKeywordQuarantineResult(
            quarantine = KeywordQuarantineSummary.from(released),
            releasedAt = now
        )
    }

    /**
     * 수동 개입은 이후 실행 결과를 해석하는 기준이 되므로 해제 직전 상태를 남긴다.
     */
    private fun logRelease(quarantine: KeywordQuarantine) {
        log.info(
            "Releasing keyword quarantine: targetType={}, keyword={}, consecutiveFailures={}, quarantinedAt={}",
            quarantine.targetType,
            quarantine.keyword.value,
            quarantine.consecutiveFailures,
            quarantine.quarantinedAt
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(ReleaseKeywordQuarantineService::class.java)
    }
}
