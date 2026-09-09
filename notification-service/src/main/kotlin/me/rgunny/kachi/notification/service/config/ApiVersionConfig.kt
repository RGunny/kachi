package me.rgunny.kachi.notification.service.config

import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiVersions
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ApiVersionConfigurer
import org.springframework.web.reactive.config.PathMatchConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * notification HTTP API에 path segment 기반 버전 정책을 적용하는 설정.
 *
 * controller는 resource path와 지원 version만 선언하고,
 * 공통 `/api/{version}` prefix는 여기서 조합한다.
 */
@Configuration
class ApiVersionConfig : WebFluxConfigurer {

    /** 
     * Actuator 같은 framework endpoint를 제외하고 notification web adapter에만 API prefix를 적용한다.
     */
    override fun configurePathMatching(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(ApiVersions.PATH_PREFIX) { handlerType ->
            handlerType.packageName.startsWith("me.rgunny.kachi.notification.service.adapter.inbound.web")
        }
    }

    /**
     * `/api/v1/...`의 두 번째 path segment를 읽어 handler의 `version = "1"` 조건과 매칭한다.
     */
    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .usePathSegment(1)
            .addSupportedVersions(ApiVersions.V1)
            .setVersionRequired(true)
    }
}
