package me.rgunny.kachi.ai.application.service.quarantine

import java.time.Clock
import java.time.Instant
import me.rgunny.kachi.ai.application.exception.StoryQuarantineNotFoundException
import me.rgunny.kachi.ai.application.exception.StoryQuarantineNotReleasableException
import me.rgunny.kachi.ai.application.port.inbound.quarantine.ReleaseStoryQuarantineUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseStoryQuarantineCommand
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseStoryQuarantineResult
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.StoryQuarantineSnapshot
import me.rgunny.kachi.ai.application.port.outbound.quarantine.StoryQuarantinePersistencePort
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 격리된 story를 다시 요약 대상으로 되돌리는 유스케이스.
 */
@Service
class ReleaseStoryQuarantineService(
    private val storyQuarantinePersistencePort: StoryQuarantinePersistencePort,
    private val clock: Clock
) : ReleaseStoryQuarantineUseCase {

    override suspend fun release(command: ReleaseStoryQuarantineCommand): ReleaseStoryQuarantineResult {
        // 1. 실패한 적 없는 story는 기록 자체가 없다. 해제할 대상이 없다.
        val quarantine = storyQuarantinePersistencePort.findByStoryId(command.storyId)
            ?: throw StoryQuarantineNotFoundException(command.storyId)

        // 2. 추적 중이거나 이미 해제된 기록은 해제 대상이 아니다.
        if (!quarantine.isQuarantined) {
            throw StoryQuarantineNotReleasableException(command.storyId, quarantine.status)
        }

        val now = Instant.now(clock)
        logRelease(quarantine)

        // 3. 연속 실패 누적은 도메인이 0으로 되돌린다. 다음 트리거부터 다시 대상에 들어온다.
        val released = storyQuarantinePersistencePort.save(quarantine.release(now))

        return ReleaseStoryQuarantineResult(
            quarantine = StoryQuarantineSnapshot.from(released),
            releasedAt = now
        )
    }

    /**
     * 해제 직전의 격리 상태를 로그로 남긴다.
     */
    private fun logRelease(quarantine: StoryQuarantine) {
        log.info(
            "Releasing story quarantine: storyId={}, consecutiveFailures={}, quarantinedAt={}",
            quarantine.storyId.value,
            quarantine.consecutiveFailures,
            quarantine.quarantinedAt
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(ReleaseStoryQuarantineService::class.java)
    }
}
