package me.rgunny.kachi.story

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.rgunny.kachi.story.adapter.outbound.tei.TeiHttpClient
import me.rgunny.kachi.story.adapter.outbound.tei.judge.TeiRerankJudge
import me.rgunny.kachi.story.application.port.outbound.embedding.EmbeddingPort
import me.rgunny.kachi.story.application.port.outbound.judge.StoryLinkJudge
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.support.TestStubServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@Import(StoryServiceTestContainersConfig::class)
class StoryServiceApplicationTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    @Qualifier("teiEmbeddingStub")
    private lateinit var embeddingStub: TestStubServer

    @Autowired
    @Qualifier("teiRerankerStub")
    private lateinit var rerankerStub: TestStubServer

    /**
     * 기동 probe가 stub의 실제 `/info` 응답으로 통과해 추론 층이 조립된 채 뜨는지가 이 테스트의 뜻이다.
     */
    @Test
    fun contextLoads() {
        assertEquals(EmbeddingModel.BGE_M3, context.getBean(EmbeddingPort::class.java).model)
        assertTrue(context.getBean(StoryLinkJudge::class.java) is TeiRerankJudge)
        assertEquals(2, context.getBean("teiClients", List::class.java).size)
        val health = context.getBean(ReactiveHealthContributorRegistry::class.java)
        assertTrue(health.getContributor("tei-embedding") != null)
        assertTrue(health.getContributor("tei-reranker") != null)
        assertEquals(1, embeddingStub.requestCount(TeiHttpClient.INFO_PATH))
        assertEquals(1, rerankerStub.requestCount(TeiHttpClient.INFO_PATH))
    }
}
