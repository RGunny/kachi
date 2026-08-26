package me.rgunny.kachi.user.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.inbound.web.dto.RegisterSubscriptionRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.SubscriptionResponse
import me.rgunny.kachi.user.adapter.inbound.web.dto.UpdateSubscriptionRequest
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.port.inbound.subscription.ListSubscriptionsUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.RegisterSubscriptionUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.UpdateSubscriptionUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.model.ListSubscriptionsQuery
import me.rgunny.kachi.user.application.port.inbound.subscription.model.RegisterSubscriptionCommand
import me.rgunny.kachi.user.application.port.inbound.subscription.model.UpdateSubscriptionCommand
import me.rgunny.kachi.user.domain.SubscriptionId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 인증 사용자의 키워드 구독 API.
 * 경로는 `/me/keywords`를 유지하되 인입은 구독이다.
 */
@RestController
class SubscriptionController(
    private val registerSubscriptionUseCase: RegisterSubscriptionUseCase,
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase,
    private val listSubscriptionsUseCase: ListSubscriptionsUseCase
) {

    @GetMapping(ApiPaths.ME_KEYWORDS, version = ApiVersions.V1)
    fun listMySubscriptions(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser
    ): ResponseEntity<ApiResponse<List<SubscriptionResponse>>> {
        val response = listSubscriptionsUseCase.list(ListSubscriptionsQuery(authenticatedUser.userId))
            .map(SubscriptionResponse::from)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @PostMapping(ApiPaths.ME_KEYWORDS, version = ApiVersions.V1)
    fun registerMySubscription(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser,
        @Valid @RequestBody request: RegisterSubscriptionRequest
    ): ResponseEntity<ApiResponse<SubscriptionResponse>> {
        val result = registerSubscriptionUseCase.register(
            RegisterSubscriptionCommand(
                userId = authenticatedUser.userId,
                name = request.name,
                channels = request.channels
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SubscriptionResponse.from(result)))
    }

    @PatchMapping(ApiPaths.ME_KEYWORD, version = ApiVersions.V1)
    fun updateMySubscription(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser,
        @PathVariable subscriptionId: UUID,
        @Valid @RequestBody request: UpdateSubscriptionRequest
    ): ResponseEntity<ApiResponse<SubscriptionResponse>> {
        val result = updateSubscriptionUseCase.update(
            UpdateSubscriptionCommand(
                subscriptionId = SubscriptionId.of(subscriptionId),
                userId = authenticatedUser.userId,
                channels = request.channels,
                enabled = request.enabled
            )
        )

        return ResponseEntity.ok(ApiResponse.success(SubscriptionResponse.from(result)))
    }
}
