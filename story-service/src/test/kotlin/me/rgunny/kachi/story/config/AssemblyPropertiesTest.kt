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

/** 운영 yaml 바인딩과 정책 변환을 보는 테스트. */
@DisplayName("AssemblyProperties")
class AssemblyPropertiesTest {

    @Test
    @DisplayName("운영 application.yaml의 kachi.story.assembly 값이 골드셋 측정값으로 바인딩된다")
    fun bindProductionYaml() {
        val policy = bindProduction().toPolicy()

        assertEquals(0.70, policy.thetaHigh)
        assertEquals(0.60, policy.thetaLow)
        assertEquals(0.30, policy.thetaJudge)
        assertEquals(10, policy.candidateLimit)
        assertEquals(5, policy.recentArticles)
        assertEquals(Duration.ofHours(72), policy.candidateWindow)
        assertEquals(500, policy.maxArticles)
        assertEquals(3, policy.maxCasRetries)
    }

    @Test
    @DisplayName("정책 변환에서 검증이 걸린다")
    fun validateOnConversion() {
        val properties = bindProduction().copy(thetaHigh = 0.5)

        assertFailsWith<IllegalArgumentException> { properties.toPolicy() }
    }

    private fun bindProduction(): AssemblyProperties {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(ClassPathResource("application.yaml"))
        val yaml = requireNotNull(factory.getObject()) { "application.yaml을 읽지 못했습니다" }
        val source = MapConfigurationPropertySource(yaml.entries.associate { (key, value) -> key.toString() to value.toString() })

        return Binder(source).bind(AssemblyProperties.PREFIX, AssemblyProperties::class.java).get()
    }
}
