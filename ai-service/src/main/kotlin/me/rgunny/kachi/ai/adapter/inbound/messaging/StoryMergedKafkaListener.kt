package me.rgunny.kachi.ai.adapter.inbound.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.inbound.messaging.exception.InvalidStoryEventMessageException
import me.rgunny.kachi.ai.application.port.inbound.story.ApplyStoryMergeUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeCommand
import me.rgunny.kachi.story.contract.StoryMergedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * story.merged Kafka 인입 adapter.
 *
 * 계약을 읽어 병합 반영 명령으로 바꾸고 상태 이동은 유스케이스에 맡긴다.
 * 메시지 자체의 결함은 [InvalidStoryEventMessageException]으로 던지고, 그 밖의 실패는 그대로 던진다.
 */
@Component
class StoryMergedKafkaListener(
    private val applyStoryMergeUseCase: ApplyStoryMergeUseCase,
    private val jsonMapper: JsonMapper
) {

    @KafkaListener(
        topics = ["\${kachi.ai.consumer.topics.story-merged}"],
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
            applyStoryMergeUseCase.apply(command)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw retrying(command, exception)
        }

        log.info(
            "story merge consumed mergedStoryId={} storyId={} replayed={} movedPending={}",
            command.mergedStoryId.value,
            command.storyId.value,
            result.replayed,
            result.movedPendingCount
        )
    }

    /**
     * 실패를 warn 로그로 남기고 그대로 돌려준다.
     */
    private fun retrying(command: ApplyStoryMergeCommand, exception: Exception): Exception {
        log.warn(
            "story merge apply failed and will be retried mergedStoryId={} storyId={}",
            command.mergedStoryId.value,
            command.storyId.value,
            exception
        )

        return exception
    }

    private fun readCommand(payload: String): ApplyStoryMergeCommand {
        return try {
            StoryEventMapper.toMergeCommand(jsonMapper.readValue(payload, StoryMergedEvent::class.java))
        } catch (exception: IllegalArgumentException) {
            throw InvalidStoryEventMessageException("invalid story merged event", exception)
        } catch (exception: Exception) {
            throw InvalidStoryEventMessageException("invalid story merged payload", exception)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(StoryMergedKafkaListener::class.java)
    }
}
