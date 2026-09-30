package me.rgunny.kachi.story.config

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

/** 항목 검증과 운영 yaml 바인딩을 보는 테스트. */
@DisplayName("CandidateIndexProperties")
class CandidateIndexPropertiesTest {

    @Test
    @DisplayName("host와 collection-prefix는 비어 있을 수 없다")
    fun rejectBlankHostAndPrefix() {
        assertFailsWith<IllegalArgumentException> { properties(host = " ") }
        assertFailsWith<IllegalArgumentException> { properties(collectionPrefix = "") }
    }

    @Test
    @DisplayName("grpc-port는 1~65535이고 timeout은 양수여야 한다")
    fun rejectPortAndTimeoutOutOfRange() {
        assertFailsWith<IllegalArgumentException> { properties(grpcPort = 0) }
        assertFailsWith<IllegalArgumentException> { properties(grpcPort = 65536) }
        assertFailsWith<IllegalArgumentException> { properties(timeout = Duration.ZERO) }
    }

    @Test
    @DisplayName("컬렉션 이름은 prefix와 모델 code를 잇는다")
    fun collectionName() {
        assertEquals("story-articles-bge-m3", properties().collectionName("bge-m3"))
    }

    @Test
    @DisplayName("운영 application.yaml의 kachi.story.index 값이 그대로 바인딩된다")
    fun bindProductionYaml() {
        val properties = productionYamlBinder().bind(CandidateIndexProperties.PREFIX, CandidateIndexProperties::class.java).get()

        assertEquals("localhost", properties.host)
        assertEquals(6334, properties.grpcPort)
        assertEquals(false, properties.tls)
        assertEquals(Duration.ofSeconds(5), properties.timeout)
        assertEquals("story-articles", properties.collectionPrefix)
    }

    private fun productionYamlBinder(): Binder {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(ClassPathResource("application.yaml"))
        val yaml = requireNotNull(factory.getObject()) { "application.yaml을 읽지 못했습니다" }
        val source = MapConfigurationPropertySource(
            // 운영 yaml의 ${ENV:default} 자리표시는 Binder가 풀지 않으므로 기본값으로 바꾼다.
            yaml.entries.associate { (key, value) -> key.toString() to value.toString().replace(Regex("\\$\\{[^:}]*:([^}]*)}"), "$1") }
        )

        return Binder(source)
    }

    private fun properties(
        host: String = "localhost",
        grpcPort: Int = 6334,
        timeout: Duration = Duration.ofSeconds(5),
        collectionPrefix: String = "story-articles"
    ): CandidateIndexProperties {
        return CandidateIndexProperties(host = host, grpcPort = grpcPort, tls = false, timeout = timeout, collectionPrefix = collectionPrefix)
    }
}
