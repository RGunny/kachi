package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.adapter.`in`.web.ApiVersions
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ApiVersionConfigurer
import org.springframework.web.reactive.config.PathMatchConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class ApiVersionConfig : WebFluxConfigurer {

    override fun configurePathMatching(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(ApiVersions.PATH_PREFIX) { handlerType ->
            handlerType.packageName.startsWith("me.rgunny.kachi.ai.adapter.in.web")
        }
    }

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .usePathSegment(1)
            .addSupportedVersions(ApiVersions.V1)
            .setVersionRequired(true)
    }
}
