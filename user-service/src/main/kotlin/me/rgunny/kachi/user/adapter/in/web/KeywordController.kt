package me.rgunny.kachi.user.adapter.`in`.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.`in`.web.dto.KeywordResponse
import me.rgunny.kachi.user.adapter.`in`.web.dto.RegisterKeywordRequest
import me.rgunny.kachi.user.adapter.`in`.web.dto.UpdateKeywordRequest
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordUseCase
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordUseCase
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class KeywordController(
    private val registerKeywordUseCase: RegisterKeywordUseCase,
    private val updateKeywordUseCase: UpdateKeywordUseCase
) {

    @PostMapping("/users/{userId}/keywords", version = ApiVersions.V1)
    fun register(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: RegisterKeywordRequest
    ): ResponseEntity<KeywordResponse> {
        val result = registerKeywordUseCase.register(
            RegisterKeywordCommand(
                userId = UserId.of(userId),
                name = request.name
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(KeywordResponse.from(result))
    }

    @PatchMapping("/keywords/{keywordId}", version = ApiVersions.V1)
    fun update(
        @PathVariable keywordId: UUID,
        @Valid @RequestBody request: UpdateKeywordRequest
    ): KeywordResponse {
        val result = updateKeywordUseCase.update(
            UpdateKeywordCommand(
                keywordId = KeywordId.of(keywordId),
                name = request.name,
                enabled = request.enabled
            )
        )

        return KeywordResponse.from(result)
    }
}
