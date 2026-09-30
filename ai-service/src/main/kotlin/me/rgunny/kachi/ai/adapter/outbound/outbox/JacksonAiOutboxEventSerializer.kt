package me.rgunny.kachi.ai.adapter.outbound.outbox

import me.rgunny.kachi.ai.application.port.outbound.outbox.AiOutboxEventSerializer
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.AiOutboxEvent
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.KeywordQuarantinedEvent
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.SummaryCreatedEvent
import me.rgunny.kachi.ai.contract.AiFailureReason
import me.rgunny.kachi.ai.contract.AiKeywordQuarantinedEvent
import me.rgunny.kachi.ai.contract.AiSummaryCreatedEvent
import me.rgunny.kachi.ai.contract.AiSummarySentiment
import me.rgunny.kachi.ai.contract.AiTargetType
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import me.rgunny.kachi.ai.domain.run.AiFailureReason as DomainFailureReason

/**
 * application 이벤트를 계약 객체로 옮긴 뒤 JSON으로 쓰는 serializer.
 *
 * 계약 객체로 한 번 옮기는 이유는 payload 형식을 application 모델에서 떼어 두기 위해서다.
 */
@Component
class JacksonAiOutboxEventSerializer(
    private val jsonMapper: JsonMapper
) : AiOutboxEventSerializer {

    override fun serialize(event: AiOutboxEvent): String {
        val contract: Any = when (event) {
            is SummaryCreatedEvent -> toContract(event)
            is KeywordQuarantinedEvent -> toContract(event)
        }

        return jsonMapper.writeValueAsString(contract)
    }

    private fun toContract(event: SummaryCreatedEvent): AiSummaryCreatedEvent {
        return AiSummaryCreatedEvent(
            schemaVersion = event.schemaVersion,
            summaryId = event.summaryId.toString(),
            keyword = event.keyword,
            title = event.title,
            content = event.content,
            sentiment = toContract(event.sentiment),
            sourceNewsCount = event.sourceNewsCount,
            provider = event.provider,
            model = event.model,
            promptVersion = event.promptVersion,
            createdAt = event.createdAt
        )
    }

    private fun toContract(event: KeywordQuarantinedEvent): AiKeywordQuarantinedEvent {
        return AiKeywordQuarantinedEvent(
            schemaVersion = event.schemaVersion,
            quarantineId = event.quarantineId.toString(),
            targetType = toContract(event.targetType),
            keyword = event.keyword,
            consecutiveFailures = event.consecutiveFailures,
            lastFailureReason = toContract(event.lastFailureReason),
            quarantinedAt = event.quarantinedAt
        )
    }

    private fun toContract(sentiment: NewsSummarySentiment): AiSummarySentiment {
        return when (sentiment) {
            NewsSummarySentiment.POSITIVE -> AiSummarySentiment.POSITIVE
            NewsSummarySentiment.NEUTRAL -> AiSummarySentiment.NEUTRAL
            NewsSummarySentiment.NEGATIVE -> AiSummarySentiment.NEGATIVE
            NewsSummarySentiment.UNKNOWN -> AiSummarySentiment.UNKNOWN
        }
    }

    private fun toContract(targetType: AiRunTargetType): AiTargetType {
        return when (targetType) {
            AiRunTargetType.KEYWORD_EXPANSION -> AiTargetType.KEYWORD_EXPANSION
            AiRunTargetType.NEWS_SUMMARY -> AiTargetType.NEWS_SUMMARY
        }
    }

    private fun toContract(reason: DomainFailureReason): AiFailureReason {
        return when (reason) {
            DomainFailureReason.TIMEOUT -> AiFailureReason.TIMEOUT
            DomainFailureReason.RATE_LIMITED -> AiFailureReason.RATE_LIMITED
            DomainFailureReason.CLIENT_ERROR -> AiFailureReason.CLIENT_ERROR
            DomainFailureReason.SERVER_ERROR -> AiFailureReason.SERVER_ERROR
            DomainFailureReason.NETWORK_ERROR -> AiFailureReason.NETWORK_ERROR
            DomainFailureReason.INVALID_RESPONSE -> AiFailureReason.INVALID_RESPONSE
            DomainFailureReason.PROVIDER_UNAVAILABLE -> AiFailureReason.PROVIDER_UNAVAILABLE
            // 둘 다 4xx이고 격리 카운트는 입력 탓 실패만 올리므로 격리 이벤트에 실릴 경로가 없다. 계약에 값을 늘리지 않고 4xx로 보낸다.
            DomainFailureReason.MODEL_NOT_FOUND,
            DomainFailureReason.ACCOUNT_ERROR -> AiFailureReason.CLIENT_ERROR
            DomainFailureReason.EMPTY_INPUT -> AiFailureReason.EMPTY_INPUT
            DomainFailureReason.UNKNOWN -> AiFailureReason.UNKNOWN
        }
    }
}
