package me.rgunny.kachi.collector.adapter.outbound.outbox

import me.rgunny.kachi.collector.application.port.outbound.outbox.CollectorOutboxEventSerializer
import me.rgunny.kachi.collector.application.port.outbound.outbox.model.CollectorOutboxEvent
import me.rgunny.kachi.collector.application.port.outbound.outbox.model.NewsCollectedEvent
import me.rgunny.kachi.collector.contract.CollectorNewsCollectedEvent
import me.rgunny.kachi.collector.contract.CollectorNewsSource
import me.rgunny.kachi.collector.domain.NewsSource
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * application 이벤트를 계약 객체로 옮긴 뒤 JSON으로 쓴다.
 *
 * 계약 객체로 한 번 옮기는 이유는 payload 형식을 application 모델에서 떼어 두기 위해서다.
 * enum은 이름 문자열로 넘기지 않고 값마다 짝을 지어, 도메인 enum이 바뀌면 여기서 컴파일이 멈춘다.
 */
@Component
class JacksonCollectorOutboxEventSerializer(
    private val jsonMapper: JsonMapper
) : CollectorOutboxEventSerializer {

    override fun serialize(event: CollectorOutboxEvent): String {
        val contract: Any = when (event) {
            is NewsCollectedEvent -> toContract(event)
        }

        return jsonMapper.writeValueAsString(contract)
    }

    private fun toContract(event: NewsCollectedEvent): CollectorNewsCollectedEvent {
        return CollectorNewsCollectedEvent(
            schemaVersion = event.schemaVersion,
            newsId = event.newsId.toString(),
            source = toContract(event.source),
            title = event.title,
            excerpt = event.excerpt,
            url = event.url,
            language = event.language,
            publishedAt = event.publishedAt,
            collectedAt = event.collectedAt,
            matchedKeywords = event.matchedKeywords
        )
    }

    private fun toContract(source: NewsSource): CollectorNewsSource {
        return when (source) {
            NewsSource.GOOGLE -> CollectorNewsSource.GOOGLE
            NewsSource.NAVER -> CollectorNewsSource.NAVER
            NewsSource.FINNHUB -> CollectorNewsSource.FINNHUB
        }
    }
}
