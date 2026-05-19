package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.`in`.web.ApiVersions
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ApiVersionConfig : WebMvcConfigurer {

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(ApiVersions.PATH_PREFIX) { handlerType ->
            handlerType.packageName.startsWith("me.rgunny.kachi.user.adapter.in.web")
        }
    }

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .usePathSegment(1)
            .addSupportedVersions(ApiVersions.V1)
            .setVersionRequired(true)
    }
}
