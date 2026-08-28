package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.recipient.RecipientResolverPort
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel

/**
 * 고정 결과나 예외를 돌려주고 호출 인자를 기록하는 recipient resolver fake.
 */
class FakeRecipientResolverPort(
    var result: ResolvedRecipient = AvailableRecipient("https://hooks.slack.test/services/resolved"),
    var failure: Throwable? = null,
) : RecipientResolverPort {
    val calls = mutableListOf<Pair<String, NotificationChannel>>()

    override suspend fun resolve(recipientId: String, channel: NotificationChannel): ResolvedRecipient {
        calls += recipientId to channel
        failure?.let { throw it }
        return result
    }
}
