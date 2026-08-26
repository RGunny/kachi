package me.rgunny.kachi.user.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.inbound.web.dto.CompleteTelegramLinkRequest
import me.rgunny.kachi.user.application.port.inbound.binding.CompleteTelegramLinkUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.CompleteTelegramLinkCommand
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 채널 바인딩 internal API.
 *
 * 인증 없이 열려 있으며 봇 수신기가 `/start <token>`을 받았을 때 호출한다.
 */
@RestController
class InternalChannelBindingController(
    private val completeTelegramLinkUseCase: CompleteTelegramLinkUseCase
) {

    @PostMapping(ApiPaths.INTERNAL_TELEGRAM_LINK, version = ApiVersions.V1)
    fun completeTelegramLink(
        @Valid @RequestBody request: CompleteTelegramLinkRequest
    ): ResponseEntity<Unit> {
        completeTelegramLinkUseCase.complete(CompleteTelegramLinkCommand(token = request.token, chatId = request.chatId))

        return ResponseEntity.noContent().build()
    }
}
