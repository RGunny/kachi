package me.rgunny.kachi.user.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.inbound.web.dto.CompleteTelegramLinkRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.ResolvedChannelBindingResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.inbound.binding.CompleteTelegramLinkUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.CompleteTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.internal.ResolveChannelBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolveChannelBindingQuery
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 채널 바인딩 internal API.
 *
 * 인증 없이 열려 있다.
 * 발송자가 수신자와 채널로 주소를 풀 때, 봇 수신기가 `/start <token>`을 받았을 때 호출한다.
 */
@RestController
class InternalChannelBindingController(
    private val resolveChannelBindingUseCase: ResolveChannelBindingUseCase,
    private val completeTelegramLinkUseCase: CompleteTelegramLinkUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_CHANNEL_BINDING, version = ApiVersions.V1)
    fun resolveChannelBinding(
        @PathVariable userId: UUID,
        @PathVariable channel: SubscriptionChannel
    ): ResponseEntity<ApiResponse<ResolvedChannelBindingResponse>> {
        val result = resolveChannelBindingUseCase.resolve(
            ResolveChannelBindingQuery(userId = UserId.of(userId), channel = channel)
        )

        return ResponseEntity.ok(ApiResponse.success(ResolvedChannelBindingResponse.from(result)))
    }

    @PostMapping(ApiPaths.INTERNAL_TELEGRAM_LINK, version = ApiVersions.V1)
    fun completeTelegramLink(
        @Valid @RequestBody request: CompleteTelegramLinkRequest
    ): ResponseEntity<Unit> {
        completeTelegramLinkUseCase.complete(CompleteTelegramLinkCommand(token = request.token, chatId = request.chatId))

        return ResponseEntity.noContent().build()
    }
}
