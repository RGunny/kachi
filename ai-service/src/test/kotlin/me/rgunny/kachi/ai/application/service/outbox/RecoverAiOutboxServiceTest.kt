package me.rgunny.kachi.ai.application.service.outbox

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.AiOutboxErrorCode
import me.rgunny.kachi.ai.application.exception.AiOutboxNotFoundException
import me.rgunny.kachi.ai.application.exception.AiOutboxNotRecoverableException
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxCommand
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxClaim
import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import me.rgunny.kachi.ai.fake.FakeAiOutboxPersistencePort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("RecoverAiOutboxService")
class RecoverAiOutboxServiceTest {
    private val outboxPersistencePort = FakeAiOutboxPersistencePort()
    private val service = RecoverAiOutboxService(
        outboxPersistencePort = outboxPersistencePort,
        clock = AiTestFixture.CLOCK
    )

    @Test
    @DisplayName("DEAD 행을 발행 대기 상태로 되돌려 저장한다")
    fun recoverDeadOutbox() = runBlocking {
        val dead = outboxWith(AiOutboxStatus.DEAD)
        outboxPersistencePort.store(dead)

        val result = service.recover(RecoverAiOutboxCommand(dead.id))

        assertEquals(AiOutboxStatus.PENDING, result.outbox.status)
        assertEquals(0, result.outbox.retryCount)
        assertEquals(AiTestFixture.NOW, result.outbox.nextRetryAt)
        assertNull(result.outbox.lastError)
        assertEquals(AiTestFixture.NOW, result.recoveredAt)

        val recovered = outboxPersistencePort.recoverCalls.single()
        assertEquals(AiOutboxStatus.PENDING, recovered.status)
        assertEquals(0, recovered.retryCount)
        assertEquals(AiTestFixture.NOW, recovered.nextRetryAt)
    }

    @Test
    @DisplayName("행이 없으면 복구 대상이 없다고 실패한다")
    fun rejectMissingOutbox() = runBlocking {
        val error = assertFailsWith<AiOutboxNotFoundException> {
            service.recover(RecoverAiOutboxCommand(AiOutboxId.newId()))
        }

        assertEquals(AiOutboxErrorCode.OUTBOX_NOT_FOUND, error.errorCode)
        assertEquals(0, outboxPersistencePort.recoverCalls.size)
    }

    @ParameterizedTest
    @EnumSource(value = AiOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["DEAD"])
    @DisplayName("DEAD가 아닌 행은 복구하지 않는다")
    fun rejectNotDeadStatus(status: AiOutboxStatus) = runBlocking {
        val outbox = outboxWith(status)
        outboxPersistencePort.store(outbox)

        val error = assertFailsWith<AiOutboxNotRecoverableException> {
            service.recover(RecoverAiOutboxCommand(outbox.id))
        }

        assertEquals(AiOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, error.errorCode)
        assertEquals(0, outboxPersistencePort.recoverCalls.size)
    }

    @Test
    @DisplayName("저장이 조건에 밀리면 복구하지 못했다고 실패한다")
    fun rejectWhenRecoveryIsLost() = runBlocking {
        val dead = outboxWith(AiOutboxStatus.DEAD)
        outboxPersistencePort.store(dead)
        outboxPersistencePort.recoverResult = false

        val error = assertFailsWith<AiOutboxNotRecoverableException> {
            service.recover(RecoverAiOutboxCommand(dead.id))
        }

        assertEquals(AiOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, error.errorCode)
        assertEquals(1, outboxPersistencePort.recoverCalls.size)
    }

    private fun outboxWith(status: AiOutboxStatus): AiOutbox {
        return AiTestFixture.restoredOutbox(
            status = status,
            retryCount = if (status == AiOutboxStatus.PENDING) 0 else 5,
            lastError = if (status == AiOutboxStatus.DEAD) "broker down" else null,
            publishedAt = AiTestFixture.NOW.takeIf { status == AiOutboxStatus.PUBLISHED },
            claim = AiOutboxClaim(
                claimedBy = AiTestFixture.RELAY_PUBLISHER_ID,
                claimedAt = AiTestFixture.NOW
            ).takeIf { status == AiOutboxStatus.PUBLISHING }
        )
    }
}
