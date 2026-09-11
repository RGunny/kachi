package me.rgunny.kachi.story

import io.qdrant.client.QdrantClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.qdrant.index.QdrantCandidateIndexAdapter
import me.rgunny.kachi.story.adapter.outbound.qdrant.index.QdrantCollection
import me.rgunny.kachi.story.adapter.outbound.tei.TeiHttpClient
import me.rgunny.kachi.story.adapter.outbound.tei.judge.TeiRerankJudge
import me.rgunny.kachi.story.application.port.outbound.embedding.EmbeddingPort
import me.rgunny.kachi.story.application.port.outbound.index.CandidateIndexPort
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
     * 기동 probe가 stub의 실제 `/info` 응답으로 통과하고 Qdrant 컬렉션이 만들어진 채 뜨는지가 이 테스트의 뜻이다.
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

        assertTrue(context.getBean(CandidateIndexPort::class.java) is QdrantCandidateIndexAdapter)
        assertTrue(health.getContributor("qdrant") != null)
        val collection = context.getBean(QdrantCollection::class.java)
        assertEquals("story-articles-bge-m3", collection.name)
        assertTrue(runBlocking { context.getBean(QdrantClient::class.java).collectionExistsAsync(collection.name).await() })
    }
}
