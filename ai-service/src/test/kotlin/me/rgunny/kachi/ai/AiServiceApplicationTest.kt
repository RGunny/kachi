package me.rgunny.kachi.ai

import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@Import(AiServiceApplicationTest.MissingPortTestConfig::class)
class AiServiceApplicationTest {

    @Test
    fun contextLoads() {
    }

    @TestConfiguration
    class MissingPortTestConfig {

        @Bean
        fun keywordReaderPort(): KeywordReaderPort {
            return object : KeywordReaderPort {
                override suspend fun findActiveKeywords(): List<AiKeyword> {
                    return emptyList()
                }
            }
        }
    }
}
