package me.rgunny.kachi.collector.application.service.outbox

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.application.exception.CollectorOutboxErrorCode
import me.rgunny.kachi.collector.application.exception.CollectorOutboxNotFoundException
import me.rgunny.kachi.collector.application.exception.CollectorOutboxNotRecoverableException
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxCommand
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxClaim
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import me.rgunny.kachi.collector.fake.FakeCollectorOutboxPersistencePort
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("RecoverCollectorOutboxService")
class RecoverCollectorOutboxServiceTest {
    private val outboxPersistencePort = FakeCollectorOutboxPersistencePort()
    private val service = RecoverCollectorOutboxService(
        outboxPersistencePort = outboxPersistencePort,
        clock = CollectorTestFixture.CLOCK
    )

    @Test
    @DisplayName("DEAD 행을 발행 대기 상태로 되돌려 저장한다")
    fun recoverDeadOutbox() = runBlocking {
        val dead = outboxWith(CollectorOutboxStatus.DEAD)
        outboxPersistencePort.store(dead)

        val result = service.recover(RecoverCollectorOutboxCommand(dead.id))

        assertEquals(CollectorOutboxStatus.PENDING, result.outbox.status)
        assertEquals(0, result.outbox.retryCount)
        assertEquals(CollectorTestFixture.NOW, result.outbox.nextRetryAt)
        assertNull(result.outbox.lastError)
        assertEquals(CollectorTestFixture.NOW, result.recoveredAt)

        val recovered = outboxPersistencePort.recoverCalls.single()
        assertEquals(CollectorOutboxStatus.PENDING, recovered.status)
        assertEquals(0, recovered.retryCount)
        assertEquals(CollectorTestFixture.NOW, recovered.nextRetryAt)
    }

    @Test
    @DisplayName("행이 없으면 복구 대상이 없다고 실패한다")
    fun rejectMissingOutbox() = runBlocking {
        val error = assertFailsWith<CollectorOutboxNotFoundException> {
            service.recover(RecoverCollectorOutboxCommand(CollectorOutboxId.newId()))
        }

        assertEquals(CollectorOutboxErrorCode.OUTBOX_NOT_FOUND, error.errorCode)
        assertEquals(0, outboxPersistencePort.recoverCalls.size)
    }

    @ParameterizedTest
    @EnumSource(value = CollectorOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["DEAD"])
    @DisplayName("DEAD가 아닌 행은 복구하지 않는다")
    fun rejectNotDeadStatus(status: CollectorOutboxStatus) = runBlocking {
        val outbox = outboxWith(status)
        outboxPersistencePort.store(outbox)

        val error = assertFailsWith<CollectorOutboxNotRecoverableException> {
            service.recover(RecoverCollectorOutboxCommand(outbox.id))
        }

        assertEquals(CollectorOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, error.errorCode)
        assertEquals(0, outboxPersistencePort.recoverCalls.size)
    }

    @Test
    @DisplayName("저장이 조건에 밀리면 복구하지 못했다고 실패한다")
    fun rejectWhenRecoveryIsLost() = runBlocking {
        val dead = outboxWith(CollectorOutboxStatus.DEAD)
        outboxPersistencePort.store(dead)
        outboxPersistencePort.recoverResult = false

        val error = assertFailsWith<CollectorOutboxNotRecoverableException> {
            service.recover(RecoverCollectorOutboxCommand(dead.id))
        }

        assertEquals(CollectorOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, error.errorCode)
        assertEquals(1, outboxPersistencePort.recoverCalls.size)
    }

    private fun outboxWith(status: CollectorOutboxStatus): CollectorOutbox {
        return CollectorTestFixture.restoredOutbox(
            status = status,
            retryCount = if (status == CollectorOutboxStatus.PENDING) 0 else 5,
            lastError = if (status == CollectorOutboxStatus.DEAD) "broker down" else null,
            publishedAt = CollectorTestFixture.NOW.takeIf { status == CollectorOutboxStatus.PUBLISHED },
            claim = CollectorOutboxClaim(
                claimedBy = CollectorTestFixture.RELAY_PUBLISHER_ID,
                claimedAt = CollectorTestFixture.NOW
            ).takeIf { status == CollectorOutboxStatus.PUBLISHING }
        )
    }
}
