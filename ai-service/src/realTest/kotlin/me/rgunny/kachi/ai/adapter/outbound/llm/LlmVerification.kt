package me.rgunny.kachi.ai.adapter.outbound.llm

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata
import me.rgunny.kachi.ai.domain.llm.LlmBilling
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.LlmUse
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.LlmProfileCandidates
import me.rgunny.kachi.ai.support.OllamaTags
import me.rgunny.kachi.ai.support.ProviderModelList
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.web.reactive.function.client.bodyToMono
import java.net.URI
import java.util.UUID
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * 프로파일의 후보 모델을 실제 제공자에 불러 확인하는 테스트 본문.
 *
 * 모델마다 제공자의 모델 목록에 code가 있는지(토큰 0), 후보로 오른 용도마다 생성 1회가 계약대로 돌아오는지 본다.
 * Ollama는 tag가 이동 alias라 digest도 남긴다. 서버나 secret이 없으면 skip하지 않고 실패한다.
 * 프로파일은 시스템 프로퍼티 `kachi.llm.profile`이고 없으면 local이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class LlmVerification(
    private val includes: (LlmBilling) -> Boolean
) {

    private val candidates = LlmProfileCandidates(profile = System.getProperty(PROFILE_PROPERTY, DEFAULT_PROFILE))

    fun models(): List<LlmModel> {
        return candidates.properties.candidateModels.filter { includes(billingOf(it)) }
    }

    fun useCandidates(): List<Arguments> {
        return candidates.useCandidates
            .filter { (_, model) -> includes(billingOf(model)) }
            .map { (use, model) -> Arguments.of(use, model) }
    }

    @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
    @MethodSource("models")
    @DisplayName("제공자의 모델 목록에 후보 모델의 code가 있다")
    fun modelIsListed(model: LlmModel) {
        val listed = candidates.providerWebClient(model.provider)
            .get()
            .uri(MODELS_PATH)
            .retrieve()
            .bodyToMono<ProviderModelList>()
            .block()
            ?.data
            .orEmpty()
            .map { it.id }

        assertTrue(model.code in listed, "${model.provider.code}의 모델 목록에 ${model.code}가 없습니다: $listed")

        report("listed", model, "billing=${billingOf(model)} baseUrl=${candidates.properties.providerOf(model).baseUrl}")
        if (model.provider == LlmProvider.OLLAMA) {
            report("digest", model, "digest=${ollamaDigest(model)}")
        }
    }

    @ParameterizedTest(name = "{0} {1}", allowZeroInvocations = true)
    @MethodSource("useCandidates")
    @DisplayName("후보로 오른 용도마다 실제 생성 1회가 계약대로 돌아온다")
    fun generates(
        use: LlmUse,
        model: LlmModel
    ) {
        val guarded = candidates.model(model)

        val (generated, latency) = measureTimedValue {
            runBlocking {
                when (use) {
                    LlmUse.NEWS_SUMMARY -> summarize(guarded)
                    LlmUse.KEYWORD_EXPANSION -> expand(guarded)
                }
            }
        }

        val metadata = generated.metadata
        assertTrue(metadata.tokenUsage.inputTokens > 0, "input token이 보고되지 않았습니다")
        assertTrue(metadata.tokenUsage.outputTokens > 0, "output token이 보고되지 않았습니다")

        report(
            "generated",
            model,
            "use=$use served=${metadata.model} latency=${latency.inWholeMilliseconds}ms " +
                "tokens=${metadata.tokenUsage.inputTokens}/${metadata.tokenUsage.outputTokens} ${generated.outcome}"
        )
    }

    private suspend fun summarize(guarded: GuardedLlmModel): Generated {
        val result = guarded.summarizeNews(keyword = AiTestFixture.keyword(), articles = ARTICLES)

        return Generated(
            metadata = result.metadata,
            outcome = "sentiment=${result.sentiment} title=\"${result.title}\""
        )
    }

    private suspend fun expand(guarded: GuardedLlmModel): Generated {
        val result = guarded.expandKeyword(keyword = AiTestFixture.keyword(), maxExpansions = MAX_EXPANSIONS)

        return Generated(
            metadata = result.metadata,
            outcome = "keywords=${result.expandedKeywords.map { it.value }}"
        )
    }

    /**
     * OpenAI 규격 목록에는 digest가 없어 Ollama 고유 API로 묻는다. 규격 경로(`/v1`)를 뗀 주소다.
     */
    private fun ollamaDigest(model: LlmModel): String {
        val baseUrl = candidates.properties.providerOf(model).baseUrl.removeSuffix("/").removeSuffix(OPENAI_PATH_PREFIX)
        val tags = candidates.providerWebClient(model.provider)
            .get()
            .uri(URI.create("$baseUrl$OLLAMA_TAGS_PATH"))
            .retrieve()
            .bodyToMono<OllamaTags>()
            .block()
            ?.models
            .orEmpty()

        return tags.firstOrNull { it.name == model.code }?.digest
            ?: error("Ollama tags에 ${model.code}가 없습니다: ${tags.map { it.name }}")
    }

    private fun billingOf(model: LlmModel): LlmBilling = candidates.properties.providerOf(model).billing

    private fun report(
        event: String,
        model: LlmModel,
        detail: String
    ) {
        println("[realTest] profile=${candidates.profile} $event model=${model.name} code=${model.qualifiedCode} $detail")
    }

    /**
     * 생성 1회의 결과. [outcome]은 보고에만 쓴다.
     */
    private data class Generated(
        val metadata: LlmGenerationMetadata,
        val outcome: String
    )

    private companion object {
        const val PROFILE_PROPERTY = "kachi.llm.profile"
        const val DEFAULT_PROFILE = "local"
        const val MODELS_PATH = "/models"
        const val OPENAI_PATH_PREFIX = "/v1"
        const val OLLAMA_TAGS_PATH = "/api/tags"
        const val MAX_EXPANSIONS = 3

        /** 요약 입력. 어느 모델이든 답할 수 있는 흔한 키워드의 헤드라인 세 개다. */
        val ARTICLES = listOf(
            AiTestFixture.newsArticle(
                id = UUID.fromString("018f0000-0000-7000-8000-000000000101"),
                title = "NVIDIA reports record data center revenue as AI demand accelerates"
            ),
            AiTestFixture.newsArticle(
                id = UUID.fromString("018f0000-0000-7000-8000-000000000102"),
                title = "NVIDIA unveils next-generation GPU architecture at GTC"
            ),
            AiTestFixture.newsArticle(
                id = UUID.fromString("018f0000-0000-7000-8000-000000000103"),
                title = "Regulators review NVIDIA export licenses for advanced chips"
            )
        )
    }
}
