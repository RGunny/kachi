package me.rgunny.kachi.story.adapter.inbound.messaging

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.collector.contract.CollectorNewsCollectedEvent
import me.rgunny.kachi.collector.contract.CollectorNewsSource
import me.rgunny.kachi.story.adapter.inbound.messaging.exception.InvalidNewsCollectedMessageException
import me.rgunny.kachi.story.application.exception.CandidateIndexException
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.application.exception.StoryAssemblyErrorCode
import me.rgunny.kachi.story.application.exception.StoryAssemblyException
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.index.CandidateIndexFailure
import me.rgunny.kachi.story.domain.index.CandidateIndexFailureCode
import me.rgunny.kachi.story.domain.inference.InferenceFailure
import me.rgunny.kachi.story.domain.inference.InferenceFailureCode
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import me.rgunny.kachi.story.fake.FakeAssembleStoryUseCase
import me.rgunny.kachi.story.fixture.StoryTestFixture.NEWS_ID
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * listener가 payload를 명령으로 바꾸는 것과 실패를 재시도 여부로 가르는 것을 본다.
 *
 * Kafka는 없고 listener 메서드를 직접 부른다.
 */
@DisplayName("NewsCollectedKafkaListener")
class NewsCollectedKafkaListenerTest {
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val useCase = FakeAssembleStoryUseCase()
    private val listener = NewsCollectedKafkaListener(assembleStoryUseCase = useCase, jsonMapper = jsonMapper)

    @Test
    @DisplayName("계약 payload를 붙일 기사 명령으로 바꿔 유스케이스에 넘긴다")
    fun consumeValidPayload() {
        listener.consume(jsonMapper.writeValueAsString(event()))

        val command = useCase.commands.single()
        assertEquals(NEWS_ID, command.newsId)
        assertEquals(ArticleSource.NAVER, command.source)
        assertEquals("NVIDIA 실적 발표", command.title)
    }

    @Test
    @DisplayName("읽을 수 없는 payload는 재시도하지 않는 예외다")
    fun rejectMalformedPayload() {
        assertFailsWith<InvalidNewsCollectedMessageException> { listener.consume("{not json") }
        assertFailsWith<InvalidNewsCollectedMessageException> { listener.consume("""{"schemaVersion":1}""") }

        assertEquals(0, useCase.commands.size)
    }

    @Test
    @DisplayName("지원하지 않는 schemaVersion은 재시도하지 않는 예외다")
    fun rejectUnsupportedSchemaVersion() {
        val payload = jsonMapper.writeValueAsString(event(schemaVersion = CollectorNewsCollectedEvent.CURRENT_SCHEMA_VERSION + 1))

        val error = assertFailsWith<InvalidNewsCollectedMessageException> { listener.consume(payload) }

        assertIs<IllegalArgumentException>(error.cause)
        assertEquals(0, useCase.commands.size)
    }

    @Test
    @DisplayName("추론 서버가 기사 텍스트를 거부한 실패는 재시도하지 않는 예외로 바꾼다")
    fun rejectInputAttributedInferenceFailure() {
        val cause = InferenceException(InferenceFailure(code = InferenceFailureCode.INFERENCE_INPUT_INVALID, target = InferenceTarget.EMBEDDING))
        useCase.failure = cause

        val error = assertFailsWith<InvalidNewsCollectedMessageException> { listener.consume(jsonMapper.writeValueAsString(event())) }

        assertSame(cause, error.cause)
    }

    @ParameterizedTest
    @EnumSource(value = InferenceFailureCode::class, names = ["INFERENCE_INPUT_EMPTY", "INFERENCE_PAYLOAD_TOO_LARGE", "INFERENCE_INPUT_INVALID"], mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("기사 탓이 아닌 추론 실패는 그대로 던져 같은 레코드를 다시 처리하게 한다")
    fun propagateOtherInferenceFailures(code: InferenceFailureCode) {
        val cause = InferenceException(InferenceFailure(code = code, target = InferenceTarget.EMBEDDING))
        useCase.failure = cause

        val error = assertFailsWith<InferenceException> { listener.consume(jsonMapper.writeValueAsString(event())) }

        assertSame(cause, error)
    }

    @Test
    @DisplayName("색인 실패와 경합 상한 초과는 그대로 던진다")
    fun propagateIndexAndAssemblyFailures() {
        val payload = jsonMapper.writeValueAsString(event())

        useCase.failure = CandidateIndexException(CandidateIndexFailure(code = CandidateIndexFailureCode.INDEX_REQUEST_REJECTED))
        assertFailsWith<CandidateIndexException> { listener.consume(payload) }

        useCase.failure = StoryAssemblyException(StoryAssemblyErrorCode.ASSEMBLY_CONFLICT_EXHAUSTED)
        assertFailsWith<StoryAssemblyException> { listener.consume(payload) }
    }

    @Test
    @DisplayName("코루틴 취소는 그대로 전파한다")
    fun propagateCancellation() {
        useCase.failure = CancellationException("shutting down")

        assertFailsWith<CancellationException> { listener.consume(jsonMapper.writeValueAsString(event())) }
    }

    private fun event(
        schemaVersion: Int = CollectorNewsCollectedEvent.CURRENT_SCHEMA_VERSION
    ): CollectorNewsCollectedEvent {
        return CollectorNewsCollectedEvent(
            schemaVersion = schemaVersion,
            newsId = NEWS_ID.value.toString(),
            source = CollectorNewsSource.NAVER,
            title = "NVIDIA 실적 발표",
            excerpt = "엔비디아가 2분기 실적을 발표했다",
            url = "https://kachi.com/news/1",
            language = "ko",
            publishedAt = NOW.minus(Duration.ofHours(1)),
            collectedAt = NOW,
            matchedKeywords = listOf("nvidia")
        )
    }
}
