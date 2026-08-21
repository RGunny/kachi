package me.rgunny.kachi.ai.adapter.outbound.llm

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.config.LlmProviderMode
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fake.NamedLlmProviderPort
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("RoutingLlmProvider")
class RoutingLlmProviderTest {

    @Test
    @DisplayName("single-random mode는 등록된 provider 중 하나를 호출한다")
    fun callSingleRandomProvider() = runBlocking {
        val providers = listOf(
            NamedLlmProviderPort("openrouter"),
            NamedLlmProviderPort("groq"),
            NamedLlmProviderPort("mistral")
        )
        val router = RoutingLlmProvider(
            providers = providers,
            mode = LlmProviderMode.SINGLE_RANDOM,
            random = Random(1)
        )

        val result = router.expandKeyword(
            keyword = AiKeyword.of("NVIDIA"),
            maxExpansions = 3
        )

        assertEquals(1, providers.count { it.expandCallCount == 1 })
        assertEquals(result.metadata.provider.value, providers.first { it.expandCallCount == 1 }.provider)
    }

    @Test
    @DisplayName("provider가 없으면 생성할 수 없다")
    fun failWhenProvidersAreEmpty() {
        assertFailsWith<IllegalArgumentException> {
            RoutingLlmProvider(
                providers = emptyList(),
                mode = LlmProviderMode.SINGLE_RANDOM
            )
        }
    }

    @Test
    @DisplayName("aggregate mode는 아직 호출하지 않는다")
    fun aggregateModeIsNotImplemented() = runBlocking {
        val router = RoutingLlmProvider(
            providers = listOf(NamedLlmProviderPort("openrouter")),
            mode = LlmProviderMode.AGGREGATE
        )

        assertFailsWith<UnsupportedOperationException> {
            router.expandKeyword(
                keyword = AiKeyword.of("NVIDIA"),
                maxExpansions = 3
            )
        }
    }

}
