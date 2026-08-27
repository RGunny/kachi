package me.rgunny.kachi.user.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.inbound.web.dto.ChannelBindingResponse
import me.rgunny.kachi.user.adapter.inbound.web.dto.RegisterWebhookBindingRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.TelegramLinkResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.port.inbound.binding.IssueTelegramLinkUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.ListChannelBindingsUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.RegisterWebhookBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.RevokeChannelBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.IssueTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.ListChannelBindingsQuery
import me.rgunny.kachi.user.application.port.inbound.binding.model.RegisterWebhookBindingCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.RevokeChannelBindingCommand
import me.rgunny.kachi.user.domain.SubscriptionChannel
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 인증 사용자의 채널 바인딩 API.
 *
 * PUT은 채널의 연결 방식에 따라 갈린다.
 * 주소를 직접 받는 채널은 본문의 URL로 바로 ACTIVE가 되고,
 * 연결 링크 방식 채널은 본문 없이 링크를 돌려받는다.
 */
@RestController
class ChannelBindingController(
    private val registerWebhookBindingUseCase: RegisterWebhookBindingUseCase,
    private val issueTelegramLinkUseCase: IssueTelegramLinkUseCase,
    private val revokeChannelBindingUseCase: RevokeChannelBindingUseCase,
    private val listChannelBindingsUseCase: ListChannelBindingsUseCase
) {

    @GetMapping(ApiPaths.ME_CHANNEL_BINDINGS, version = ApiVersions.V1)
    fun listMyChannelBindings(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser
    ): ResponseEntity<ApiResponse<List<ChannelBindingResponse>>> {
        val response = listChannelBindingsUseCase.list(ListChannelBindingsQuery(authenticatedUser.userId))
            .map(ChannelBindingResponse::from)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @PutMapping(ApiPaths.ME_CHANNEL_BINDING, version = ApiVersions.V1)
    fun bindMyChannel(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser,
        @PathVariable channel: SubscriptionChannel,
        @Valid @RequestBody(required = false) request: RegisterWebhookBindingRequest?
    ): ResponseEntity<ApiResponse<out Any>> {
        if (channel == SubscriptionChannel.TELEGRAM) {
            val result = issueTelegramLinkUseCase.issue(IssueTelegramLinkCommand(authenticatedUser.userId))

            return ResponseEntity.ok(ApiResponse.success(TelegramLinkResponse.from(result)))
        }

        requireNotNull(request) { "webhookUrl이 필요합니다" }

        val result = registerWebhookBindingUseCase.register(
            RegisterWebhookBindingCommand(
                userId = authenticatedUser.userId,
                channel = channel,
                webhookUrl = request.webhookUrl
            )
        )

        return ResponseEntity.ok(ApiResponse.success(ChannelBindingResponse.from(result)))
    }

    @DeleteMapping(ApiPaths.ME_CHANNEL_BINDING, version = ApiVersions.V1)
    fun revokeMyChannelBinding(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser,
        @PathVariable channel: SubscriptionChannel
    ): ResponseEntity<Unit> {
        revokeChannelBindingUseCase.revoke(RevokeChannelBindingCommand(authenticatedUser.userId, channel))

        return ResponseEntity.noContent().build()
    }
}
