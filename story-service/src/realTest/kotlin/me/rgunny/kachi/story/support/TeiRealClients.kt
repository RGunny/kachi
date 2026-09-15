package me.rgunny.kachi.story.support

import me.rgunny.kachi.story.adapter.outbound.tei.TeiHttpClient
import me.rgunny.kachi.story.config.InferenceProperties
import me.rgunny.kachi.story.config.TeiWebClients
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.core.io.ClassPathResource

/** local 프로파일의 추론 서버 설정을 Spring 컨텍스트 없이 바인딩하고, 운영과 같은 WebClient로 서버 둘의 클라이언트를 만드는 헬퍼. */
class TeiRealClients {

    val properties: InferenceProperties = bind()

    val embedding: TeiHttpClient = TeiHttpClient(
        webClient = TeiWebClients.forServer(properties.embedding.server),
        target = InferenceTarget.EMBEDDING,
        expectedDimension = properties.embedding.model.dimension
    )

    val judge: TeiHttpClient = TeiHttpClient(
        webClient = TeiWebClients.forServer(requireNotNull(properties.judge.server) { "local 프로파일에 원격 judge 설정이 없습니다" }),
        target = InferenceTarget.JUDGE
    )

    private fun bind(): InferenceProperties {
        val sources = MutablePropertySources().apply {
            addLast(
                SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    HashMap<String, Any>(System.getenv())
                )
            )
            addLast(yamlSource("application-$PROFILE.yaml"))
            addLast(yamlSource("application.yaml"))
        }

        return Binder(ConfigurationPropertySources.from(sources), PropertySourcesPlaceholdersResolver(sources))
            .bind(InferenceProperties.PREFIX, InferenceProperties::class.java)
            .get()
    }

    private fun yamlSource(fileName: String): PropertiesPropertySource {
        val resource = ClassPathResource(fileName)
        require(resource.exists()) { "${fileName}이 classpath에 없습니다" }

        val factory = YamlPropertiesFactoryBean()
        factory.setResources(resource)

        return PropertiesPropertySource(fileName, requireNotNull(factory.getObject()) { "${fileName}을 읽지 못했습니다" })
    }

    private companion object {
        const val PROFILE = "local"
    }
}
