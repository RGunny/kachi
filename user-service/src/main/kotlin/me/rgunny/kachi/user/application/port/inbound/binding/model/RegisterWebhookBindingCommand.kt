package me.rgunny.kachi.user.application.port.inbound.binding.model

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * [webhookUrl]은 사용자가 입력한 원문이다.
 * 채널별 형식 검증은 서비스가 한다.
 */
data class RegisterWebhookBindingCommand(
    val userId: UserId,
    val channel: SubscriptionChannel,
    val webhookUrl: String
)
