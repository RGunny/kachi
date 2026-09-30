package me.rgunny.kachi.story.config

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

/** 운영 yaml 바인딩과 정책 변환을 보는 테스트. */
@DisplayName("StoryJobsProperties")
class StoryJobsPropertiesTest {

    @Test
    @DisplayName("운영 application.yaml의 kachi.story.jobs 값이 바인딩된다")
    fun bindProductionYaml() {
        val properties = bindProduction()

        assertTrue(properties.close.enabled)
        assertEquals(Duration.ofMinutes(10), properties.close.interval)
        assertEquals(Duration.ofMinutes(1), properties.close.initialDelay)
        assertEquals(Duration.ofHours(48), properties.close.closeAfter)
        assertEquals(100, properties.close.batchLimit)
        assertTrue(properties.merge.enabled)
        assertEquals(Duration.ofMinutes(10), properties.merge.interval)
        assertEquals(Duration.ofMinutes(2), properties.merge.initialDelay)
        assertEquals(Duration.ofHours(24), properties.merge.scanWindow)
        assertEquals(200, properties.merge.scanLimit)
        assertTrue(properties.cleanup.enabled)
        assertEquals(Duration.ofHours(1), properties.cleanup.interval)
        assertEquals(Duration.ofMinutes(5), properties.cleanup.initialDelay)
    }

    @Test
    @DisplayName("정책 변환에서 검증이 걸린다")
    fun validateOnConversion() {
        val close = bindProduction().close.copy(closeAfter = Duration.ZERO)

        assertFailsWith<IllegalArgumentException> { close.toPolicy() }
    }

    @Test
    @DisplayName("interval이 0이면 바인딩할 수 없다")
    fun rejectZeroInterval() {
        assertFailsWith<IllegalArgumentException> {
            bindProduction().close.copy(interval = Duration.ZERO)
        }
    }

    @Test
    @DisplayName("병합 정책 변환에서 검증이 걸린다")
    fun validateMergeOnConversion() {
        val merge = bindProduction().merge.copy(scanWindow = Duration.ZERO)

        assertFailsWith<IllegalArgumentException> { merge.toPolicy() }
    }

    @Test
    @DisplayName("병합 interval이 0이면 바인딩할 수 없다")
    fun rejectZeroMergeInterval() {
        assertFailsWith<IllegalArgumentException> {
            bindProduction().merge.copy(interval = Duration.ZERO)
        }
    }

    private fun bindProduction(): StoryJobsProperties {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(ClassPathResource("application.yaml"))
        val yaml = requireNotNull(factory.getObject()) { "application.yaml을 읽지 못했습니다" }
        val source = MapConfigurationPropertySource(yaml.entries.associate { (key, value) -> key.toString() to value.toString() })

        return Binder(source).bind(StoryJobsProperties.PREFIX, StoryJobsProperties::class.java).get()
    }
}
