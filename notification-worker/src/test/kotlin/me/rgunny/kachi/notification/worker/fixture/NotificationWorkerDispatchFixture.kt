package me.rgunny.kachi.notification.worker.fixture

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import me.rgunny.kachi.notification.application.service.DispatchNotificationService
import me.rgunny.kachi.notification.application.service.NotificationSenderRouter
import me.rgunny.kachi.notification.contract.NotificationDispatchEvent
import me.rgunny.kachi.notification.contract.NotificationChannel as ContractNotificationChannel
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.NotificationDispatchKafkaListener
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetrics
import me.rgunny.kachi.notification.worker.config.DiscordNotificationSenderConfig
import me.rgunny.kachi.notification.worker.config.MockNotificationSenderConfig
import me.rgunny.kachi.notification.worker.config.NotificationDispatchProperties
import me.rgunny.kachi.notification.worker.config.NotificationRecipientConfig
import me.rgunny.kachi.notification.worker.config.NotificationRecipientProperties
import me.rgunny.kachi.notification.worker.config.NotificationSenderProperties
import me.rgunny.kachi.notification.worker.config.NotificationWorkerCoreConfig
import me.rgunny.kachi.notification.worker.config.NotificationWorkerProperties
import me.rgunny.kachi.notification.worker.config.SlackNotificationSenderConfig
import me.rgunny.kachi.notification.worker.config.TelegramNotificationSenderConfig
import me.rgunny.kachi.notification.worker.fake.FakeNotificationDeduplicationPort
import me.rgunny.kachi.notification.worker.fake.FakeNotificationDispatchPersistencePort
import me.rgunny.kachi.notification.worker.fake.FakeNotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.worker.fake.FakeNotificationPersistencePort
import me.rgunny.kachi.notification.worker.fake.InMemoryRecipientAddressCache
import me.rgunny.kachi.notification.worker.support.TestVendorResponse
import me.rgunny.kachi.notification.worker.support.TestVendorServer
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.notification.domain.NotificationOrigin

/**
 * notification-worker dispatch 통합 테스트용 조립 fixture.
 *
 * 실제 worker config를 통해 Slack/Discord/Telegram sender, mock sender, 수신 주소 resolver를 만들고,
 * persistence/redis/idempotency 같은 외부 저장소 port만 fake로 대체한다.
 * [TestVendorServer]는 vendor와 user-service 역할을 같이 맡는다.
 */
class NotificationWorkerDispatchFixture(
    private val vendorServer: TestVendorServer,
    private val clock: Clock,
) {
    val persistence = FakeNotificationPersistencePort()
    val deduplication = FakeNotificationDeduplicationPort()
    val recipientCache = InMemoryRecipientAddressCache()
    val listener: NotificationDispatchKafkaListener
    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    init {
        val workerProperties = NotificationWorkerProperties(workerId = "test-worker")
        val dispatchProperties = dispatchProperties()
        val senderProperties = senderProperties(vendorServer)
        val recipientProperties = recipientProperties(vendorServer)
        val slackConfig = SlackNotificationSenderConfig()
        val discordConfig = DiscordNotificationSenderConfig()
        val telegramConfig = TelegramNotificationSenderConfig()
        val mockConfig = MockNotificationSenderConfig()
        val recipientConfig = NotificationRecipientConfig()
        val coreConfig = NotificationWorkerCoreConfig()

        val slackSender = slackConfig.slackNotificationSender(
            webClient = slackConfig.slackWebClient(senderProperties),
        )
        val discordSender = discordConfig.discordNotificationSender(
            webClient = discordConfig.discordWebClient(senderProperties),
        )
        val telegramSender = telegramConfig.telegramNotificationSender(
            webClient = telegramConfig.telegramWebClient(senderProperties),
            properties = senderProperties,
        )
        val mockSender = mockConfig.mockNotificationSender(senderProperties)
        val router = NotificationSenderRouter(listOf(slackSender, discordSender, telegramSender, mockSender))
        val retryPolicy = coreConfig.dispatchRetryPolicy(dispatchProperties)
        val dispatchPolicy = coreConfig.dispatchNotificationPolicy(workerProperties, dispatchProperties, retryPolicy)
        val recipientResolver = recipientConfig.recipientResolverPort(
            webClient = recipientConfig.recipientWebClient(recipientProperties),
            recipientAddressCache = recipientCache,
            properties = recipientProperties,
        )
        val dispatchUseCase = DispatchNotificationService(
            notificationPersistencePort = persistence,
            dispatchPersistencePort = FakeNotificationDispatchPersistencePort(persistence),
            deduplicationPort = deduplication,
            recipientResolverPort = recipientResolver,
            idempotencyKeyPort = FakeNotificationIdempotencyKeyPort(),
            senderRouter = router,
            policy = dispatchPolicy,
            clock = clock,
        )

        listener = NotificationDispatchKafkaListener(
            dispatchUseCase = dispatchUseCase,
            jsonMapper = jsonMapper,
            metrics = NotificationWorkerMetrics(SimpleMeterRegistry()),
        )
    }

    /**
     * user-service 역할의 vendor server에 그 수신자·채널의 바인딩 응답을 등록한다.
     * address가 null이면 status대로 주소 없는 응답이 된다.
     */
    fun binding(
        recipientId: String,
        channel: NotificationChannel,
        status: String,
        address: String?,
    ) {
        val addressJson = address?.let { "\"$it\"" } ?: "null"
        vendorServer.respond(
            bindingPath(recipientId, channel),
            TestVendorResponse(
                statusCode = 200,
                body = """{"success":true,"data":{"channel":"${channel.name}","status":"$status","address":$addressJson},"error":null}""",
            ),
        )
    }

    /** user-service 역할의 vendor server가 그 수신자·채널에 임의 응답을 돌려주게 한다. */
    fun bindingResponse(recipientId: String, channel: NotificationChannel, response: TestVendorResponse) {
        vendorServer.respond(bindingPath(recipientId, channel), response)
    }

    fun bindingPath(recipientId: String, channel: NotificationChannel): String {
        return "/api/v1/internal/users/$recipientId/channel-bindings/${channel.name}"
    }

    fun publishedNotification(
        channel: NotificationChannel,
        recipientId: String = UUID.randomUUID().toString(),
    ): Notification {
        return Notification.request(
            requestId = "request-${UUID.randomUUID()}",
            requester = "test-requester",
            channel = channel,
            recipientId = recipientId,
            message = MESSAGE,
            origin = NotificationOrigin.NONE,
            now = clock.instant().minusSeconds(10),
        )
            .markPublished(clock.instant().minusSeconds(5))
            .also(persistence::put)
    }

    fun payload(notification: Notification): String {
        return jsonMapper.writeValueAsString(
            NotificationDispatchEvent(
                notificationId = notification.id.id.toString(),
                requestId = notification.requestId,
                channel = ContractNotificationChannel.valueOf(notification.channel.name),
                recipientId = notification.recipientId,
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

    private fun recipientProperties(vendorServer: TestVendorServer): NotificationRecipientProperties {
        return NotificationRecipientProperties(
            cacheTtl = Duration.ofMinutes(5),
            userService = NotificationRecipientProperties.UserService(
                baseUrl = vendorServer.baseUrl,
                channelBindingPath = "/api/v1/internal/users/{userId}/channel-bindings/{channel}",
                timeout = Duration.ofSeconds(3),
                maxInMemorySize = 256 * 1024,
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
                connectTimeout = Duration.ofSeconds(2),
                responseTimeout = Duration.ofSeconds(5),
                readTimeout = Duration.ofSeconds(5),
                writeTimeout = Duration.ofSeconds(5),
                maxInMemorySize = 256 * 1024,
            ),
            discord = NotificationSenderProperties.Discord(
                enabled = true,
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
