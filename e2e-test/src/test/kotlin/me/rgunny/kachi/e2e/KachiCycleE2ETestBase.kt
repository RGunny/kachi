package me.rgunny.kachi.e2e

import me.rgunny.kachi.e2e.support.JsonHttp
import me.rgunny.kachi.e2e.support.JwtSupport
import me.rgunny.kachi.e2e.support.KachiCluster
import me.rgunny.kachi.e2e.support.KafkaSupport
import me.rgunny.kachi.e2e.support.NotificationStore
import me.rgunny.kachi.e2e.support.PrometheusMetrics
import me.rgunny.kachi.e2e.support.TestStubResponse
import me.rgunny.kachi.e2e.support.UserServiceClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * e2e 시나리오 클래스의 공통 뼈대. 클래스마다 [KachiCluster] 하나를 띄우고 끝나면 내린다.
 *
 * 테스트 메서드는 서로 순서에 기대지 않는다. 각자 고유한 사용자·키워드를 만들고 requestId로 자기 알림만 찾는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class KachiCycleE2ETestBase {
    protected val cluster = KachiCluster()
    protected val http = JsonHttp()
    protected lateinit var users: UserServiceClient
    protected lateinit var kafka: KafkaSupport
    protected lateinit var notifications: NotificationStore
    protected lateinit var workerMetrics: PrometheusMetrics

    @BeforeAll
    fun startCluster() {
        cluster.start()
        users = UserServiceClient(cluster.userService.baseUrl, http, JwtSupport(cluster.jwtSecret))
        kafka = KafkaSupport(cluster.infrastructure.kafka.bootstrapServers)
        notifications = NotificationStore(
            cluster.infrastructure.mongoUriFromHost(KachiCluster.NOTIFICATION_DATABASE),
            KachiCluster.NOTIFICATION_DATABASE
        )
        workerMetrics = PrometheusMetrics(http, cluster.worker.baseUrl)
    }

    @AfterAll
    fun stopCluster() {
        if (::notifications.isInitialized) notifications.close()
        cluster.close()
    }

    /** user-service 정규화(casefold·공백 축약)를 거쳐도 그대로인 형태로 만든다. 그래야 ai 이벤트의 keyword와 구독 조회 키가 같다. */
    protected fun uniqueKeyword(prefix: String): String = "e2e-$prefix-${UUID.randomUUID().toString().take(8)}"

    /** collector-service가 어떤 키워드에도 돌려줄 기사 [count]건을 둔다. */
    protected fun stubNews(count: Int) {
        val articles = List(count) { index ->
            mapOf(
                "id" to UUID.randomUUID().toString(),
                "source" to "GOOGLE",
                "title" to "e2e news $index",
                "url" to "https://news.example.com/${UUID.randomUUID()}",
                "publishedAt" to Instant.now().minus(Duration.ofMinutes(index + 1L)).toString(),
                "collectedAt" to Instant.now().toString(),
                "matchedKeywords" to emptyList<String>()
            )
        }
        val body = http.jsonMapper.writeValueAsString(mapOf("success" to true, "data" to articles))
        cluster.collector.respond(NEWS_PATH, TestStubResponse(statusCode = 200, body = body))
    }

    /** ai-service에 이 키워드들만 요약하라고 한다. scheduler 대신 internal API로 tick을 돌린다. 호출은 tick이 끝난 뒤 200으로 돌아온다. */
    protected fun summarize(vararg keywords: String) {
        http.post(
            "${cluster.aiService.baseUrl}/api/v1/internal/ai/news-summaries",
            mapOf("keywords" to keywords.toList())
        ).expect(200)
    }

    protected fun summaryRequestId(summaryId: String, userId: String, channel: String): String {
        return "sum:$summaryId:u:$userId:c:$channel"
    }

    protected companion object {
        const val NEWS_PATH = "/api/v1/internal/news"
        const val SLACK = "SLACK"
        const val DISCORD = "DISCORD"
        const val TELEGRAM = "TELEGRAM"
    }
}
