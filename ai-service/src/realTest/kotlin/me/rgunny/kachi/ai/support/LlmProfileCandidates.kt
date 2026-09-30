package me.rgunny.kachi.ai.support

import me.rgunny.kachi.ai.adapter.outbound.llm.GuardedLlmModel
import me.rgunny.kachi.ai.config.LlmCircuitBreakerConfig
import me.rgunny.kachi.ai.config.LlmConfig
import me.rgunny.kachi.ai.config.LlmProperties
import me.rgunny.kachi.ai.config.LlmWebClients
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.LlmUse
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.core.io.ClassPathResource
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Clock

/**
 * 프로파일 하나의 LLM 설정을 Spring 컨텍스트 없이 바인딩하고, 운영과 같은 조립으로 후보 모델을 만드는 헬퍼.
 *
 * 설정은 `application.yaml` 위에 `application-<profile>.yaml`을 덮은 것이고(`default`는 기본 yaml만), `${ENV}` 자리표시는
 * 환경변수와 env 파일([TestSecretEnvironment])로 푼다. [LlmProperties]의 검증을 그대로 지나므로 secret이 없으면 여기서 실패한다.
 * 조립은 [LlmConfig]와 [LlmCircuitBreakerConfig]의 팩터리를 그대로 부른다.
 */
class LlmProfileCandidates(
    val profile: String
) {
    val properties: LlmProperties = bind()

    /** 후보 모델마다 운영과 같은 가드·adapter·WebClient를 씌운 것. */
    val models: List<GuardedLlmModel> = assemble()

    /** 용도마다 설정된 순서의 (용도, 모델) 쌍. */
    val useCandidates: List<Pair<LlmUse, LlmModel>> = LlmUse.entries.flatMap { use ->
        properties.candidates(use).map { model -> use to model }
    }

    fun model(model: LlmModel): GuardedLlmModel = models.first { it.model == model }

    /**
     * 제공자 층의 WebClient. 생성이 아니라 제공자 자체를 묻는 요청(모델 목록)에 쓴다.
     */
    fun providerWebClient(provider: LlmProvider): WebClient {
        val settings = properties.providers.getValue(provider)

        return LlmWebClients.forProvider(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            connectTimeout = settings.connectTimeout
        )
    }

    private fun bind(): LlmProperties {
        val secrets = TestSecretEnvironment(profile)
        val sources = MutablePropertySources().apply {
            // 서비스와 같은 우선순위다. 환경변수가 yaml을 덮고, 프로파일 yaml이 기본 yaml을 덮는다.
            // 환경변수의 relaxed 매핑(KACHI_AI_LLM_...)은 소스 이름이 Spring 표준 이름일 때만 적용된다.
            addLast(
                SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    HashMap<String, Any>(System.getenv())
                )
            )
            // Spring의 default 프로파일은 파일이 없다. 그 밖의 프로파일은 파일이 있어야 한다.
            if (profile != SPRING_DEFAULT_PROFILE) {
                addLast(yamlSource("application-$profile.yaml"))
            }
            addLast(yamlSource("application.yaml"))
            // 자리표시 `${GROQ_API_KEY:}`가 env 파일의 값으로 풀리도록 마지막에 둔다.
            addLast(object : PropertySource<TestSecretEnvironment>("dotenv", secrets) {
                override fun getProperty(name: String): Any? = source.value(name)
            })
        }

        return Binder(ConfigurationPropertySources.from(sources), PropertySourcesPlaceholdersResolver(sources))
            .bind(LlmProperties.PREFIX, LlmProperties::class.java)
            .get()
    }

    private fun yamlSource(fileName: String): PropertiesPropertySource {
        val resource = ClassPathResource(fileName)
        require(resource.exists()) { "${fileName}이 classpath에 없습니다. 프로파일 '$profile'을 확인하세요" }

        val factory = YamlPropertiesFactoryBean()
        factory.setResources(resource)

        return PropertiesPropertySource(fileName, requireNotNull(factory.getObject()) { "${fileName}을 읽지 못했습니다" })
    }

    private fun assemble(): List<GuardedLlmModel> {
        val config = LlmConfig()

        return config.guardedLlmModels(
            properties = properties,
            circuitBreakerRegistry = LlmCircuitBreakerConfig().llmCircuitBreakerRegistry(properties),
            providerHoldRegistry = config.providerHoldRegistry(),
            jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build(),
            clock = Clock.systemUTC()
        )
    }

    private companion object {
        const val SPRING_DEFAULT_PROFILE = "default"
    }
}
