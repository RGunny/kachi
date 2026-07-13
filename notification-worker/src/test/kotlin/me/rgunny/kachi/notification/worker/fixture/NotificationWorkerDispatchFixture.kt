package me.rgunny.kachi.notification.worker.fixture

import me.rgunny.kachi.notification.application.service.DispatchNotificationService
import me.rgunny.kachi.notification.application.service.NotificationSenderRouter
import me.rgunny.kachi.notification.contract.NotificationDispatchEvent
import me.rgunny.kachi.notification.contract.NotificationChannel as ContractNotificationChannel
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.NotificationDispatchKafkaListener
import me.rgunny.kachi.notification.worker.config.DiscordNotificationSenderConfig
import me.rgunny.kachi.notification.worker.config.MockNotificationSenderConfig
import me.rgunny.kachi.notification.worker.config.NotificationDispatchProperties
import me.rgunny.kachi.notification.worker.config.NotificationSenderProperties
import me.rgunny.kachi.notification.worker.config.NotificationWorkerCoreConfig
import me.rgunny.kachi.notification.worker.config.NotificationWorkerProperties
import me.rgunny.kachi.notification.worker.config.SlackNotificationSenderConfig
import me.rgunny.kachi.notification.worker.config.TelegramNotificationSenderConfig
import me.rgunny.kachi.notification.worker.fake.FakeNotificationDeduplicationPort
import me.rgunny.kachi.notification.worker.fake.FakeNotificationDispatchPersistencePort
import me.rgunny.kachi.notification.worker.fake.FakeNotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.worker.fake.FakeNotificationPersistencePort
import me.rgunny.kachi.notification.worker.support.TestVendorServer
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * notification-worker dispatch 통합 테스트용 조립 fixture.
 *
 * 실제 worker config를 통해 Slack/Discord/Telegram sender와 mock sender를 만들고,
 * persistence/redis/idempotency 같은 외부 저장소 port만 fake로 대체한다.
 */
class NotificationWorkerDispatchFixture(
    vendorServer: TestVendorServer,
    private val clock: Clock,
) {
    val persistence = FakeNotificationPersistencePort()
    val deduplication = FakeNotificationDeduplicationPort()
    val listener: NotificationDispatchKafkaListener
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    init {
        val workerProperties = NotificationWorkerProperties(workerId = "test-worker")
        val dispatchProperties = dispatchProperties()
        val senderProperties = senderProperties(vendorServer)
        val slackConfig = SlackNotificationSenderConfig()
        val discordConfig = DiscordNotificationSenderConfig()
        val telegramConfig = TelegramNotificationSenderConfig()
        val mockConfig = MockNotificationSenderConfig()
        val coreConfig = NotificationWorkerCoreConfig()

        val slackSender = slackConfig.slackNotificationSender(
            webClient = slackConfig.slackWebClient(senderProperties),
            properties = senderProperties,
        )
        val discordSender = discordConfig.discordNotificationSender(
            webClient = discordConfig.discordWebClient(senderProperties),
            properties = senderProperties,
        )
        val telegramSender = telegramConfig.telegramNotificationSender(
            webClient = telegramConfig.telegramWebClient(senderProperties),
            properties = senderProperties,
        )
        val mockSender = mockConfig.mockNotificationSender(senderProperties)
        val router = NotificationSenderRouter(listOf(slackSender, discordSender, telegramSender, mockSender))
        val retryPolicy = coreConfig.dispatchRetryPolicy(dispatchProperties)
        val dispatchPolicy = coreConfig.dispatchNotificationPolicy(workerProperties, dispatchProperties, retryPolicy)
        val dispatchUseCase = DispatchNotificationService(
            notificationPersistencePort = persistence,
            dispatchPersistencePort = FakeNotificationDispatchPersistencePort(persistence),
            deduplicationPort = deduplication,
            idempotencyKeyPort = FakeNotificationIdempotencyKeyPort(),
            senderRouter = router,
            policy = dispatchPolicy,
            clock = clock,
        )

        listener = NotificationDispatchKafkaListener(
            dispatchUseCase = dispatchUseCase,
            jsonMapper = jsonMapper,
        )
    }

    fun publishedNotification(
        channel: NotificationChannel,
        recipient: String,
    ): Notification {
        return Notification.request(
            requestId = "request-${UUID.randomUUID()}",
            requester = "test-requester",
            channel = channel,
            recipient = recipient,
            message = MESSAGE,
            now = clock.instant().minusSeconds(10),
        ).also {
            it.markPublished(clock.instant().minusSeconds(5))
            persistence.put(it)
        }
    }

    fun payload(notification: Notification): String {
        return jsonMapper.writeValueAsString(
            NotificationDispatchEvent(
                notificationId = notification.id.id.toString(),
                requestId = notification.requestId,
                channel = ContractNotificationChannel.valueOf(notification.channel.name),
                recipient = notification.recipient,
                message = MESSAGE,
            )
        )
    }

    private fun dispatchProperties(): NotificationDispatchProperties {
        return NotificationDispatchProperties(
            topic = "notification.dispatch",
            groupId = "notification-worker",
            dedupeTtl = Duration.ofMinutes(5),
            idempotencyKeyTtl = Duration.ofHours(24),
            processingVisibilityTimeout = Duration.ofSeconds(30),
            recovery = NotificationDispatchProperties.Recovery(
                enabled = true,
                interval = Duration.ofSeconds(30),
                batchSize = 100,
            ),
            retry = NotificationDispatchProperties.Retry(
                maxAttempts = 3,
                backoff = Duration.ofSeconds(1),
            ),
            dlt = NotificationDispatchProperties.Dlt(
                topic = "notification.dispatch.dlt",
                groupId = "notification-worker-dlt",
            ),
        )
    }

    private fun senderProperties(vendorServer: TestVendorServer): NotificationSenderProperties {
        return NotificationSenderProperties(
            mock = NotificationSenderProperties.Mock(
                enabled = true,
                channels = listOf("SLACK", "DISCORD", "TELEGRAM", "SMS", "KAKAO", "EMAIL"),
                mode = "SUCCESS",
            ),
            slack = NotificationSenderProperties.Slack(
                enabled = true,
                webhookUrl = "${vendorServer.baseUrl}/slack",
                connectTimeout = Duration.ofSeconds(2),
                responseTimeout = Duration.ofSeconds(5),
                readTimeout = Duration.ofSeconds(5),
                writeTimeout = Duration.ofSeconds(5),
                maxInMemorySize = 256 * 1024,
            ),
            discord = NotificationSenderProperties.Discord(
                enabled = true,
                webhookUrl = "${vendorServer.baseUrl}/discord",
                connectTimeout = Duration.ofSeconds(2),
                responseTimeout = Duration.ofSeconds(5),
                readTimeout = Duration.ofSeconds(5),
                writeTimeout = Duration.ofSeconds(5),
                maxInMemorySize = 256 * 1024,
            ),
            telegram = NotificationSenderProperties.Telegram(
                enabled = true,
                baseUrl = vendorServer.baseUrl,
                botToken = "telegram-token",
                sendMessagePath = "/sendMessage",
                connectTimeout = Duration.ofSeconds(2),
                responseTimeout = Duration.ofSeconds(5),
                readTimeout = Duration.ofSeconds(5),
                writeTimeout = Duration.ofSeconds(5),
                maxInMemorySize = 256 * 1024,
            ),
        )
    }

    private companion object {
        const val MESSAGE = "hello"
    }
}
