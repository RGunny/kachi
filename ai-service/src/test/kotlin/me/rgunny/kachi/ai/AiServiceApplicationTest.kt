package me.rgunny.kachi.ai

import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.out.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunId
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

        @Bean
        fun keywordExpansionPersistencePort(): KeywordExpansionPersistencePort {
            return object : KeywordExpansionPersistencePort {
                override suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion {
                    return keywordExpansion
                }
            }
        }

        @Bean
        fun aiRunPersistencePort(): AiRunPersistencePort {
            return object : AiRunPersistencePort {
                override suspend fun findById(id: AiRunId): AiRun? {
                    return null
                }

                override suspend fun save(aiRun: AiRun): AiRun {
                    return aiRun
                }
            }
        }
    }
}
