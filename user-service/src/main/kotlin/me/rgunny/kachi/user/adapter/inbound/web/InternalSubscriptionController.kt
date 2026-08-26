package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.SubscriberResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.inbound.internal.FindSubscribersUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.FindSubscribersQuery
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 구독 internal API.
 *
 * 인증 없이 열려 있으며 알림 라우팅이 요약 이벤트의 키워드로 수신자를 물을 때 호출한다.
 */
@RestController
class InternalSubscriptionController(
    private val findSubscribersUseCase: FindSubscribersUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_SUBSCRIPTIONS, version = ApiVersions.V1)
    fun findSubscribers(
        @RequestParam keyword: String
    ): ResponseEntity<ApiResponse<List<SubscriberResponse>>> {
        val response = findSubscribersUseCase.findSubscribers(FindSubscribersQuery(keyword))
            .map(SubscriberResponse::from)

        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
