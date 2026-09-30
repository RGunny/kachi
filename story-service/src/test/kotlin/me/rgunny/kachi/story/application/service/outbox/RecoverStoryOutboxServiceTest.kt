package me.rgunny.kachi.story.application.service.outbox

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.exception.StoryOutboxErrorCode
import me.rgunny.kachi.story.application.exception.StoryOutboxNotFoundException
import me.rgunny.kachi.story.application.exception.StoryOutboxNotRecoverableException
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxCommand
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxClaim
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import me.rgunny.kachi.story.fake.FakeStoryOutboxPersistencePort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("RecoverStoryOutboxService")
class RecoverStoryOutboxServiceTest {
    private val outboxPersistencePort = FakeStoryOutboxPersistencePort()
    private val service = RecoverStoryOutboxService(
        outboxPersistencePort = outboxPersistencePort,
        clock = StoryTestFixture.CLOCK
    )

    @Test
    @DisplayName("DEAD 행을 발행 대기 상태로 되돌려 저장한다")
    fun recoverDeadOutbox() = runBlocking {
        val dead = outboxWith(StoryOutboxStatus.DEAD)
        outboxPersistencePort.store(dead)

        val result = service.recover(RecoverStoryOutboxCommand(dead.id))

        assertEquals(StoryOutboxStatus.PENDING, result.outbox.status)
        assertEquals(0, result.outbox.retryCount)
        assertEquals(StoryTestFixture.NOW, result.outbox.nextRetryAt)
        assertNull(result.outbox.lastError)
        assertEquals(StoryTestFixture.NOW, result.recoveredAt)

        val recovered = outboxPersistencePort.recoverCalls.single()
        assertEquals(StoryOutboxStatus.PENDING, recovered.status)
        assertEquals(0, recovered.retryCount)
        assertEquals(StoryTestFixture.NOW, recovered.nextRetryAt)
    }

    @Test
    @DisplayName("행이 없으면 복구 대상이 없다고 실패한다")
    fun rejectMissingOutbox() = runBlocking {
        val error = assertFailsWith<StoryOutboxNotFoundException> {
            service.recover(RecoverStoryOutboxCommand(StoryOutboxId.newId()))
        }

        assertEquals(StoryOutboxErrorCode.OUTBOX_NOT_FOUND, error.errorCode)
        assertEquals(0, outboxPersistencePort.recoverCalls.size)
    }

    @ParameterizedTest
    @EnumSource(value = StoryOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["DEAD"])
    @DisplayName("DEAD가 아닌 행은 복구하지 않는다")
    fun rejectNotDeadStatus(status: StoryOutboxStatus) = runBlocking {
        val outbox = outboxWith(status)
        outboxPersistencePort.store(outbox)

        val error = assertFailsWith<StoryOutboxNotRecoverableException> {
            service.recover(RecoverStoryOutboxCommand(outbox.id))
        }

        assertEquals(StoryOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, error.errorCode)
        assertEquals(0, outboxPersistencePort.recoverCalls.size)
    }

    @Test
    @DisplayName("저장이 조건에 밀리면 복구하지 못했다고 실패한다")
    fun rejectWhenRecoveryIsLost() = runBlocking {
        val dead = outboxWith(StoryOutboxStatus.DEAD)
        outboxPersistencePort.store(dead)
        outboxPersistencePort.recoverResult = false

        val error = assertFailsWith<StoryOutboxNotRecoverableException> {
            service.recover(RecoverStoryOutboxCommand(dead.id))
        }

        assertEquals(StoryOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, error.errorCode)
        assertEquals(1, outboxPersistencePort.recoverCalls.size)
    }

    private fun outboxWith(status: StoryOutboxStatus): StoryOutbox {
        return StoryTestFixture.restoredOutbox(
            status = status,
            retryCount = if (status == StoryOutboxStatus.PENDING) 0 else 5,
            lastError = if (status == StoryOutboxStatus.DEAD) "broker down" else null,
            publishedAt = StoryTestFixture.NOW.takeIf { status == StoryOutboxStatus.PUBLISHED },
            claim = StoryOutboxClaim(
                claimedBy = StoryTestFixture.RELAY_PUBLISHER_ID,
                claimedAt = StoryTestFixture.NOW
            ).takeIf { status == StoryOutboxStatus.PUBLISHING }
        )
    }
}
