package me.rgunny.kachi.ai

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@Import(AiServiceTestContainersConfig::class)
class AiServiceApplicationTest {

    @Test
    fun contextLoads() {
    }
}
