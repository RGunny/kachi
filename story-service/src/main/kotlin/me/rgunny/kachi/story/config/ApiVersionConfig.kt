package me.rgunny.kachi.story.config

import me.rgunny.kachi.story.adapter.inbound.web.ApiVersions
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ApiVersionConfigurer
import org.springframework.web.reactive.config.PathMatchConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * 내부 API 경로에 version prefix를 붙이는 설정.
 */
@Configuration
class ApiVersionConfig : WebFluxConfigurer {

    override fun configurePathMatching(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(ApiVersions.PATH_PREFIX) { handlerType ->
            handlerType.packageName.startsWith("me.rgunny.kachi.story.adapter.inbound.web")
        }
    }

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .usePathSegment(1)
            .addSupportedVersions(ApiVersions.V1)
            .setVersionRequired(true)
    }
}
