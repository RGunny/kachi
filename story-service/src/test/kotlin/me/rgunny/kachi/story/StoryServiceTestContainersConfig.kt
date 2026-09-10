package me.rgunny.kachi.story

import me.rgunny.kachi.story.adapter.outbound.tei.TeiHttpClient
import me.rgunny.kachi.story.config.InferenceProperties
import me.rgunny.kachi.story.support.TeiInfoJson
import me.rgunny.kachi.story.support.TestStubResponse
import me.rgunny.kachi.story.support.TestStubServer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer

/**
 * 전체 컨텍스트 통합 테스트가 쓰는 인프라 컨테이너와 추론 서버 stub.
 *
 * 같은 설정을 import하는 테스트들이 컨텍스트 캐시를 공유하도록 Mongo와 Kafka를 여기서 함께 띄운다.
 * 추론 서버는 실제 컨테이너 대신 `/info`에 고정 이미지의 실제 응답을 돌려주는 stub이다.
 */
@TestConfiguration(proxyBeanMethods = false)
class StoryServiceTestContainersConfig {

    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer {
        // transaction은 replica set에서만 동작한다.
        return MongoDBContainer("mongo:7.0").withReplicaSet()
    }

    @Bean
    @ServiceConnection
    fun kafkaContainer(): KafkaContainer {
        // 로컬 compose와 같은 이미지를 쓴다.
        return KafkaContainer("apache/kafka:3.9.1")
    }

    @Bean
    fun teiEmbeddingStub(): TestStubServer {
        return TestStubServer().apply {
            respond(TeiHttpClient.INFO_PATH, TestStubResponse(statusCode = 200, body = TeiInfoJson.EMBEDDING))
        }
    }

    @Bean
    fun teiRerankerStub(): TestStubServer {
        return TestStubServer().apply {
            respond(TeiHttpClient.INFO_PATH, TestStubResponse(statusCode = 200, body = TeiInfoJson.RERANKER))
        }
    }

    /** test yaml의 자리표시 주소를 stub 주소로 바꾼다. */
    @Bean
    fun teiStubProperties(
        @Qualifier("teiEmbeddingStub") embeddingStub: TestStubServer,
        @Qualifier("teiRerankerStub") rerankerStub: TestStubServer
    ): DynamicPropertyRegistrar {
        return DynamicPropertyRegistrar { registry ->
            registry.add("${InferenceProperties.PREFIX}.embedding.base-url") { embeddingStub.baseUrl }
            registry.add("${InferenceProperties.PREFIX}.judge.base-url") { rerankerStub.baseUrl }
        }
    }
}
