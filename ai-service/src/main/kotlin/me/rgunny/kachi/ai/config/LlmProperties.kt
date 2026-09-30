package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.adapter.outbound.llm.LlmCooldownSettings
import me.rgunny.kachi.ai.adapter.outbound.llm.LlmHoldSettings
import me.rgunny.kachi.ai.domain.llm.LlmBilling
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.LlmUse
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * LLM 호출 설정. 환경에 따라 달라지는 값과 선택만 갖는다.
 *
 * 제공자·모델·용도의 정의는 enum이 갖고, 여기는 그 정의에 머신과 계정마다 다른 값(주소·키·과금·시간)과
 * 용도마다 시도할 후보 순서를 붙인다. 켜고 끄는 스위치는 없다. 모델이 쓰인다는 것은 어느 용도의 후보에 있다는 뜻이다.
 *
 * 항목 하나 안의 값은 각 항목이 검증하고, 항목 사이의 참조는 이 클래스의 init이 검증한다.
 * 후보가 아닌 제공자와 모델은 정의만 있고 쓰이지 않는다. 조립은 [candidateModels]만 돌아야 한다.
 */
@ConfigurationProperties(prefix = LlmProperties.PREFIX)
data class LlmProperties(
    val providers: Map<LlmProvider, ProviderProperties>,
    val models: Map<LlmModel, ModelProperties>,
    val uses: Map<LlmUse, UseProperties>,
    val guard: GuardProperties
) {
    companion object {
        const val PREFIX = "kachi.ai.llm"
    }

    /** 어느 용도에든 후보로 오른 모델. 클라이언트와 서킷은 이 집합에만 만든다. */
    val candidateModels: Set<LlmModel> = uses.values.flatMap { it.candidates }.toSet()

    /**
     * 모든 용도에 후보가 있고, 후보 모델마다 시간 설정이, 그 제공자마다 환경 값과(self-hosted가 아니면) 키가 있어야 한다.
     * 빠진 것은 기동에서 실패시킨다. 첫 호출에서 알게 되면 그 사이의 tick은 조용히 실패한다.
     * 후보가 아닌 제공자는 보지 않는다. 정의만 있고 쓰지 않는 제공자의 키를 요구하면 로컬이 뜨지 않는다.
     */
    init {
        LlmUse.entries.forEach { use ->
            require(use in uses) { "LLM use ${use.name}의 candidates가 없습니다" }
        }
        candidateModels.forEach { model ->
            require(model in models) { "LLM model ${model.name}의 설정이 없습니다" }
            val provider = requireNotNull(providers[model.provider]) {
                "LLM provider ${model.provider.name}의 설정이 없습니다"
            }
            require(provider.billing == LlmBilling.SELF_HOSTED || provider.apiKey.isNotBlank()) {
                "LLM provider ${model.provider.name}의 api-key가 없습니다"
            }
        }
    }

    fun candidates(use: LlmUse): List<LlmModel> = uses.getValue(use).candidates

    fun providerOf(model: LlmModel): ProviderProperties = providers.getValue(model.provider)

    fun modelOf(model: LlmModel): ModelProperties = models.getValue(model)

    /**
     * 제공자 하나에 붙는 환경 값. [apiKey]가 있어야 하는지는 후보인지에 달렸으므로 바깥 init이 본다.
     */
    data class ProviderProperties(
        val baseUrl: String,
        val apiKey: String = "",
        val billing: LlmBilling,
        val connectTimeout: Duration
    ) {
        init {
            require(baseUrl.isNotBlank()) { "LLM provider base-url은 비어 있을 수 없습니다" }
            require(!connectTimeout.isNegative && !connectTimeout.isZero) {
                "LLM provider connect-timeout은 양수여야 합니다"
            }
        }
    }

    /**
     * 모델 하나의 시간 특성. 같은 모델도 호스팅에 따라 수십 배 다르므로 코드가 아니라 설정이다.
     *
     * [slowAfter]는 서킷 브레이커의 slow call duration threshold이고 [timeout] 전에 걸려야 의미가 있다.
     */
    data class ModelProperties(
        val timeout: Duration,
        val slowAfter: Duration
    ) {
        init {
            require(!slowAfter.isNegative && !slowAfter.isZero) { "LLM model slow-after는 양수여야 합니다" }
            require(slowAfter < timeout) { "LLM model slow-after는 timeout보다 짧아야 합니다" }
        }
    }

    /**
     * 용도 하나가 시도할 모델의 순서.
     */
    data class UseProperties(
        val candidates: List<LlmModel>
    ) {
        init {
            require(candidates.isNotEmpty()) { "LLM use의 candidates는 하나 이상이어야 합니다" }
            require(candidates.size == candidates.distinct().size) { "LLM use의 candidates에 같은 모델이 두 번 있습니다" }
        }
    }

    /**
     * 모델 호출을 막는 장치들의 정책. failure rate·slow call rate·wait duration·cooldown·hold 재탐색 간격은 어느 모델에나 같다.
     */
    data class GuardProperties(
        val circuitBreaker: LlmCircuitBreakerProperties,
        val cooldown: LlmCooldownSettings,
        val hold: LlmHoldSettings
    )
}
