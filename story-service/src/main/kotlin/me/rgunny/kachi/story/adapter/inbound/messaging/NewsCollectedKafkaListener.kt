package me.rgunny.kachi.story.adapter.inbound.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.contract.CollectorNewsCollectedEvent
import me.rgunny.kachi.story.adapter.inbound.messaging.exception.InvalidNewsCollectedMessageException
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.application.port.inbound.assembly.AssembleStoryUseCase
import me.rgunny.kachi.story.application.port.inbound.assembly.model.AttachArticleCommand
import me.rgunny.kachi.story.domain.inference.InferenceFailureAttribution
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * collector.news.collected Kafka 인입 adapter.
 *
 * 계약을 읽어 붙일 기사 명령으로 바꾸고 조립은 유스케이스에 맡긴다.
 * 기사 자체의 결함만 [InvalidNewsCollectedMessageException]으로 바꿔 던지고, 그 밖의 실패는 그대로 던져 같은 레코드를 다시 처리하게 한다.
 */
@Component
class NewsCollectedKafkaListener(
    private val assembleStoryUseCase: AssembleStoryUseCase,
    private val jsonMapper: JsonMapper
) {

    @KafkaListener(
        topics = ["\${kachi.story.consumer.topics.news-collected}"],
        groupId = "\${kachi.story.consumer.group-id}",
        containerFactory = "storyConsumerKafkaListenerContainerFactory",
        concurrency = "\${kachi.story.consumer.concurrency}",
        properties = [
            "auto.offset.reset=\${kachi.story.consumer.auto-offset-reset}",
            "max.poll.records=\${kachi.story.consumer.max-poll-records}"
        ]
    )
    fun consume(@Payload payload: String) = runBlocking {
        val command = readCommand(payload)

        val result = try {
            assembleStoryUseCase.assemble(command)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: InferenceException) {
            if (exception.failure.attribution == InferenceFailureAttribution.INPUT) {
                throw InvalidNewsCollectedMessageException("news text rejected by inference server newsId=${command.newsId.value}", exception)
            }
            throw retrying(command, exception)
        } catch (exception: Exception) {
            throw retrying(command, exception)
        }

        log.info(
            "collector news consumed newsId={} storyId={} decision={} replayed={}",
            result.newsId.value,
            result.storyId.value,
            result.decision::class.simpleName,
            result.replayed
        )
    }

    /**
     * 같은 레코드를 다시 처리하게 될 실패를 남긴다. 재시도 사이에는 이 로그만이 원인을 말해 준다.
     */
    private fun retrying(command: AttachArticleCommand, exception: Exception): Exception {
        log.warn(
            "collector news assembly failed and will be retried newsId={} title={}",
            command.newsId.value,
            command.title,
            exception
        )

        return exception
    }

    private fun readCommand(payload: String): AttachArticleCommand {
        return try {
            NewsCollectedEventMapper.toCommand(jsonMapper.readValue(payload, CollectorNewsCollectedEvent::class.java))
        } catch (exception: IllegalArgumentException) {
            throw InvalidNewsCollectedMessageException("invalid collector news collected event", exception)
        } catch (exception: Exception) {
            throw InvalidNewsCollectedMessageException("invalid collector news collected payload", exception)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NewsCollectedKafkaListener::class.java)
    }
}
