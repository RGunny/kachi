package me.rgunny.kachi.notification.application.port.outbound.persistence

import me.rgunny.kachi.notification.domain.Notification
import java.time.Instant

/**
 * 외부 채널 발송 결과 저장 port.
 *
 * Slack/Discord/Telegram 같은 vendor API 호출은 DB transaction으로 rollback할 수 없다.
 * 구현체는 vendor 호출 이후의 Notification 최종 상태(SENT/RETRY_WAIT/DEAD)를 저장하는 경계를 책임진다.
 */
interface NotificationDispatchPersistencePort {

    /**
     * vendor 호출 이후 최종 dispatch 상태를 조건부 저장한다.
     *
     * 외부 API, Redis dedupe/idempotency는 MongoDB transaction으로 되돌릴 수 없으므로,
     * 구현체는 상태 전이 계산이 끝난 Notification aggregate를 저장하는 finalize 경계가 된다.
     *
     * 단, finalize는 현재 저장소 row가 아직 caller가 claim했던 PROCESSING 상태일 때만 성공해야 한다.
     * 조건 불일치는 저장소 장애가 아니라 stale owner 결과이므로 null을 반환한다.
     */
    suspend fun saveFinalizedIfProcessingClaimMatches(
        notification: Notification,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
    ): Notification?
}
