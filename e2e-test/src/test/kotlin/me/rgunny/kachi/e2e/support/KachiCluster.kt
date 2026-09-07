package me.rgunny.kachi.e2e.support

import org.testcontainers.Testcontainers
import org.testcontainers.lifecycle.Startables
import java.security.SecureRandom
import java.util.Base64

/**
 * e2e 한 클래스가 쓰는 전체 실행 환경: 인프라 네 개, 테스트 JVM 안의 stub 두 개, 서비스 컨테이너 다섯 개.
 *
 * collector-service는 띄우지 않는다. 실제 뉴스 provider 없이는 뉴스가 생기지 않고 시드 API도 없어,
 * ai-service가 부르는 뉴스 조회 API를 stub이 받는다. LLM도 stub이다. vendor는 worker의 mock sender다.
 * Slack·Discord 주소는 user-service가 실제 vendor 접두사를 강제해 stub URL을 바인딩에 넣을 수 없다(ADR 029).
 *
 * user-service가 먼저 뜬 뒤 나머지 넷을 함께 띄운다.
 * 넷은 기동 시 user-service 주소가 비어 있으면 실패하지만 실제 호출은 요청이 있을 때 하므로,
 * 먼저 띄우는 것은 순서가 아니라 seed 데이터가 준비된 뒤 테스트가 시작되게 하기 위해서다.
 */
class KachiCluster : AutoCloseable {
    val infrastructure = KachiInfrastructure()
    val llm = TestLlmServer()
    val collector = TestStubServer()
    val jwtSecret: String = generateSecret()

    lateinit var userService: KachiServiceContainer
        private set
    lateinit var aiService: KachiServiceContainer
        private set
    lateinit var routing: KachiServiceContainer
        private set
    lateinit var notificationService: KachiServiceContainer
        private set
    lateinit var worker: KachiServiceContainer
        private set

    /** 기동에 성공한 서비스 컨테이너. 기동 도중 실패해도 여기 있는 것만 내린다. */
    private val started = mutableListOf<KachiServiceContainer>()

    fun start() {
        infrastructure.start()
        Testcontainers.exposeHostPorts(llm.port, collector.port)

        userService = KachiServiceContainer(
            module = "user-service",
            port = 8080,
            network = infrastructure.network,
            environment = commonEnvironment(database = "kachi_user") + mapOf(
                "SPRING_DATASOURCE_URL" to infrastructure.mysqlJdbcUrlInNetwork,
                "SPRING_DATASOURCE_USERNAME" to KachiInfrastructure.MYSQL_USER,
                "SPRING_DATASOURCE_PASSWORD" to KachiInfrastructure.MYSQL_PASSWORD,
                "SPRING_JPA_SHOW_SQL" to "false",
                "KACHI_JWT_SECRET" to jwtSecret,
                "KACHI_USER_TELEGRAM_BOT_USERNAME" to TELEGRAM_BOT_USERNAME
            )
        )
        userService.start()
        started += userService

        val userServiceUrl = userService.urlInNetwork
        aiService = KachiServiceContainer(
            module = "ai-service",
            port = 8083,
            network = infrastructure.network,
            environment = commonEnvironment(database = "kachi_ai") + mapOf(
                "KACHI_USER_SERVICE_BASE_URL" to userServiceUrl,
                "KACHI_COLLECTOR_SERVICE_BASE_URL" to hostUrl(collector.port),
                "KACHI_AI_EVENTS_ENABLED" to "true",
                "KACHI_AI_OUTBOX_RELAY_ENABLED" to "true",
                "KACHI_AI_OUTBOX_RELAY_INITIALDELAY" to "1s",
                "KACHI_AI_OUTBOX_RELAY_FIXEDDELAY" to "1s",
                "KACHI_AI_QUARANTINE_FAILURETHRESHOLD" to QUARANTINE_FAILURE_THRESHOLD.toString(),
                // local 프로파일의 LLM 후보는 Ollama 하나다. 그 주소만 stub으로 돌린다. 인증이 없어 키도 없다.
                "KACHI_AI_LLM_PROVIDERS_OLLAMA_BASEURL" to hostUrl(llm.port),
                // tick은 테스트가 internal API로 돌린다. local 프로파일이 켜 둔 스케줄러가 도중에 끼어들지 않게 끈다.
                "KACHI_AI_SCHEDULER_NEWSSUMMARY_ENABLED" to "false",
                "KACHI_AI_SCHEDULER_KEYWORDEXPANSION_ENABLED" to "false"
            )
        )
        routing = KachiServiceContainer(
            module = "notification-routing",
            port = 8086,
            network = infrastructure.network,
            environment = commonEnvironment(database = "kachi_notification_routing") + mapOf(
                "KACHI_USER_SERVICE_BASE_URL" to userServiceUrl
            )
        )
        notificationService = KachiServiceContainer(
            module = "notification-service",
            port = 8084,
            network = infrastructure.network,
            environment = commonEnvironment(database = NOTIFICATION_DATABASE)
        )
        worker = KachiServiceContainer(
            module = "notification-worker",
            port = 8085,
            network = infrastructure.network,
            environment = commonEnvironment(database = NOTIFICATION_DATABASE) + mapOf(
                "KACHI_USER_SERVICE_BASE_URL" to userServiceUrl,
                // 실 sender를 전부 끄면 mock sender가 모든 채널을 맡는다.
                "KACHI_NOTIFICATION_SENDER_SLACK_ENABLED" to "false",
                "KACHI_NOTIFICATION_SENDER_DISCORD_ENABLED" to "false",
                "KACHI_NOTIFICATION_SENDER_TELEGRAM_ENABLED" to "false",
                "KACHI_NOTIFICATION_TELEGRAM_BOT_TOKEN" to "e2e-unused"
            )
        )
        val followers = listOf(aiService, routing, notificationService, worker)
        Startables.deepStart(followers).join()
        started += followers
    }

    override fun close() {
        started.asReversed().forEach { it.stop() }
        started.clear()
        llm.close()
        collector.close()
        infrastructure.close()
    }

    private fun commonEnvironment(database: String): Map<String, String> {
        return mapOf(
            "SPRING_KAFKA_BOOTSTRAP_SERVERS" to infrastructure.kafkaBootstrapInNetwork,
            "SPRING_DATA_REDIS_HOST" to KachiInfrastructure.REDIS_ALIAS,
            "SPRING_DATA_REDIS_PORT" to KachiInfrastructure.REDIS_PORT.toString(),
            "SPRING_MONGODB_URI" to infrastructure.mongoUriInNetwork(database)
        )
    }

    private fun hostUrl(port: Int): String = "http://host.testcontainers.internal:$port"

    private fun generateSecret(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        const val NOTIFICATION_DATABASE = "kachi_notification"
        const val TELEGRAM_BOT_USERNAME = "kachi_e2e_bot"
        const val QUARANTINE_FAILURE_THRESHOLD = 2
    }
}
