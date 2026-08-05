package me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack.dto

/**
 * SlackWebhookClient가 Slack webhook 응답을 sender 분류에 필요한 값으로 변환한 내부 결과 모델.
 *
 * 외부 JSON DTO는 아니지만 Slack webhook 호출의 데이터 전달 타입을 한 패키지에서 찾을 수 있도록 request DTO와 함께 둔다.
 */
internal data class SlackWebhookResult(
    val statusCode: Int,
    val retryAfterMillis: Long?,
)

