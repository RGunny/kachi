package me.rgunny.kachi.notification.service.adapter.inbound.web

/** notification HTTP API의 routing pattern과 외부에 노출하는 구체 버전 경로를 함께 관리한다. */
object ApiVersions {
    /** Spring WebFlux가 controller resource path 앞에 붙이는 동적 routing pattern. */
    const val PATH_PREFIX = "/api/{version}"

    /** handler method의 `version` 조건에 사용하는 현재 지원 버전. */
    const val V1 = "1"

    /** 테스트와 경로 계약에서 사용하는 구체적인 v1 prefix. */
    const val V1_PATH_PREFIX = "/api/v$V1"
}
