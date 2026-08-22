package me.rgunny.kachi.ai.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import me.rgunny.kachi.ai.adapter.outbound.llm.GuardedLlmProvider
import me.rgunny.kachi.ai.adapter.outbound.llm.RoutingLlmProvider
import me.rgunny.kachi.ai.adapter.outbound.llm.openai.OpenAiLlmProvider
import me.rgunny.kachi.ai.adapter.outbound.llm.openai.OpenAiProviderType
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.concurrent.TimeUnit

@Configuration
class OpenAiLlmConfig {

    /**
     * enabled provider들을 mode 정책에 따라 호출하는 application port를 등록한다.
     *
     * provider마다 자기 회로를 가진 데코레이터로 감싼 뒤 router에 넘긴다. 회로를 provider 단위로 두어야
     * 한 provider의 장애가 나머지 provider의 호출을 막지 않는다.
     */
    @Bean
    fun llmProviderPort(
        properties: LlmProviderProperties,
        openAiProviders: List<OpenAiLlmProvider>,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        clock: Clock
    ): LlmProviderPort {
        return RoutingLlmProvider(
            providers = openAiProviders.map { provider ->
                GuardedLlmProvider(
                    delegate = provider,
                    provider = provider.providerName,
                    circuitBreaker = circuitBreakerRegistry.circuitBreaker(provider.providerName.value),
                    failover = properties.failover,
                    clock = clock
                )
            },
            mode = LlmProviderMode.from(properties.mode),
            clock = clock
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.ai.providers.openrouter",
        name = ["enabled"],
        havingValue = "true"
    )
    fun openrouterLlmProvider(
        properties: LlmProviderProperties,
        jsonMapper: JsonMapper
    ): OpenAiLlmProvider {
        return openAiLlmProvider(
            providerType = OpenAiProviderType.OPENROUTER,
            properties = properties,
            providerProperties = properties.openrouter,
            jsonMapper = jsonMapper
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.ai.providers.groq",
        name = ["enabled"],
        havingValue = "true"
    )
    fun groqLlmProvider(
        properties: LlmProviderProperties,
        jsonMapper: JsonMapper
    ): OpenAiLlmProvider {
        return openAiLlmProvider(
            providerType = OpenAiProviderType.GROQ,
            properties = properties,
            providerProperties = properties.groq,
            jsonMapper = jsonMapper
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.ai.providers.together",
        name = ["enabled"],
        havingValue = "true"
    )
    fun togetherLlmProvider(
        properties: LlmProviderProperties,
        jsonMapper: JsonMapper
    ): OpenAiLlmProvider {
        return openAiLlmProvider(
            providerType = OpenAiProviderType.TOGETHER,
            properties = properties,
            providerProperties = properties.together,
            jsonMapper = jsonMapper
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.ai.providers.cerebras",
        name = ["enabled"],
        havingValue = "true"
    )
    fun cerebrasLlmProvider(
        properties: LlmProviderProperties,
        jsonMapper: JsonMapper
    ): OpenAiLlmProvider {
        return openAiLlmProvider(
            providerType = OpenAiProviderType.CEREBRAS,
            properties = properties,
            providerProperties = properties.cerebras,
            jsonMapper = jsonMapper
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.ai.providers.mistral",
        name = ["enabled"],
        havingValue = "true"
    )
    fun mistralLlmProvider(
        properties: LlmProviderProperties,
        jsonMapper: JsonMapper
    ): OpenAiLlmProvider {
        return openAiLlmProvider(
            providerType = OpenAiProviderType.MISTRAL,
            properties = properties,
            providerProperties = properties.mistral,
            jsonMapper = jsonMapper
        )
    }

    private fun openAiLlmProvider(
        providerType: OpenAiProviderType,
        properties: LlmProviderProperties,
        providerProperties: OpenAiProviderProperties,
        jsonMapper: JsonMapper
    ): OpenAiLlmProvider {
        validateProvider(providerType, providerProperties)

        return OpenAiLlmProvider(
            webClient = webClient(providerProperties),
            jsonMapper = jsonMapper,
            providerType = providerType,
            properties = providerProperties,
            keywordExpansionPromptVersion = PromptVersion.of(properties.keywordExpansionPromptVersion),
            newsSummaryPromptVersion = PromptVersion.of(properties.newsSummaryPromptVersion)
        )
    }

    /**
     * enabled=true인 provider는 실제 호출 가능해야 하므로 key/model/path를 기동 시점에 검증한다.
     */
    private fun validateProvider(
        providerType: OpenAiProviderType,
        properties: OpenAiProviderProperties
    ) {
        require(properties.enabled) { "${providerType.value} LLM provider must be enabled" }
        require(properties.apiKey.isNotBlank()) { "${providerType.value} LLM provider apiKey is required" }
        require(properties.baseUrl.isNotBlank()) { "${providerType.value} LLM provider baseUrl is required" }
        require(properties.chatCompletionsPath.isNotBlank()) {
            "${providerType.value} LLM provider chatCompletionsPath is required"
        }
        require(properties.model.isNotBlank()) { "${providerType.value} LLM provider model is required" }
        require(!properties.connectTimeout.isNegative && !properties.connectTimeout.isZero) {
            "${providerType.value} LLM provider connectTimeout must be positive"
        }
        require(!properties.responseTimeout.isNegative && !properties.responseTimeout.isZero) {
            "${providerType.value} LLM provider responseTimeout must be positive"
        }
        require(!properties.readTimeout.isNegative && !properties.readTimeout.isZero) {
            "${providerType.value} LLM provider readTimeout must be positive"
        }
        require(!properties.writeTimeout.isNegative && !properties.writeTimeout.isZero) {
            "${providerType.value} LLM provider writeTimeout must be positive"
        }
        require(properties.maxInMemorySize > 0) { "${providerType.value} LLM provider maxInMemorySize must be positive" }
    }

    private fun webClient(properties: OpenAiProviderProperties): WebClient {
        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            // 외부 LLM 호출이 application 흐름을 오래 붙잡지 않도록 provider별 timeout을 적용한다.
            .clientConnector(ReactorClientHttpConnector(httpClient(properties)))
            // LLM 응답 body 역직렬화에 사용할 memory buffer 상한을 둔다.
            .codecs { it.defaultCodecs().maxInMemorySize(properties.maxInMemorySize) }
            .build()
    }

    /**
     * Reactor Netty 기반 HTTP client에 연결/응답/read/write timeout을 적용한다.
     */
    private fun httpClient(properties: OpenAiProviderProperties): HttpClient {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout.toMillis().toInt())
            .responseTimeout(properties.responseTimeout)
            .doOnConnected { connection ->
                connection
                    .addHandlerLast(ReadTimeoutHandler(properties.readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(properties.writeTimeout.toMillis(), TimeUnit.MILLISECONDS))
            }
    }
}
