package me.rgunny.kachi.ai.adapter.inbound.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.inbound.messaging.exception.InvalidStoryEventMessageException
import me.rgunny.kachi.ai.application.port.inbound.story.RecordStoryArticleUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.CreatedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.FailedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SkippedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryResult
import me.rgunny.kachi.story.contract.StoryArticleAttachedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * story.article.attached Kafka 인입 adapter.
 *
 * 계약을 읽어 기사 기록 명령으로 바꾸고 기록과 즉시 요약은 유스케이스에 맡긴다.
 * 메시지 자체의 결함은 [InvalidStoryEventMessageException]으로 던지고, 그 밖의 실패는 그대로 던진다.
 */
@Component
class StoryArticleAttachedKafkaListener(
    private val recordStoryArticleUseCase: RecordStoryArticleUseCase,
    private val jsonMapper: JsonMapper
) {

    @KafkaListener(
        topics = ["\${kachi.ai.consumer.topics.story-article-attached}"],
        groupId = "\${kachi.ai.consumer.group-id}",
        containerFactory = "storyEventKafkaListenerContainerFactory",
        concurrency = "\${kachi.ai.consumer.concurrency}",
        properties = [
            "auto.offset.reset=\${kachi.ai.consumer.auto-offset-reset}",
            "max.poll.records=\${kachi.ai.consumer.max-poll-records}"
        ]
    )
    fun consume(@Payload payload: String) = runBlocking {
        val command = readCommand(payload)

        val result = try {
            recordStoryArticleUseCase.record(command)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw retrying(command, exception)
        }

        log.info(
            "story article consumed newsId={} storyId={} replayed={} summary={}",
            command.newsId,
            result.storyId.value,
            result.replayed,
            summaryOutcome(result.summary)
        )
    }

    /**
     * 실패를 warn 로그로 남기고 그대로 돌려준다.
     */
    private fun retrying(command: RecordStoryArticleCommand, exception: Exception): Exception {
        log.warn(
            "story article record failed and will be retried newsId={} storyId={}",
            command.newsId,
            command.storyId.value,
            exception
        )

        return exception
    }

    private fun readCommand(payload: String): RecordStoryArticleCommand {
        return try {
            StoryEventMapper.toRecordCommand(jsonMapper.readValue(payload, StoryArticleAttachedEvent::class.java))
        } catch (exception: IllegalArgumentException) {
            throw InvalidStoryEventMessageException("invalid story article attached event", exception)
        } catch (exception: Exception) {
            throw InvalidStoryEventMessageException("invalid story article attached payload", exception)
        }
    }

    private fun summaryOutcome(summary: SummarizeStoryResult?): String {
        return when (summary) {
            null -> "NOT_TRIGGERED"
            is CreatedStorySummaryResult -> "CREATED(v${summary.version}, ${summary.developmentKind})"
            is SkippedStorySummaryResult -> "SKIPPED(${summary.reason})"
            is FailedStorySummaryResult -> "FAILED(${summary.reason}, quarantined=${summary.quarantined})"
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(StoryArticleAttachedKafkaListener::class.java)
    }
}
