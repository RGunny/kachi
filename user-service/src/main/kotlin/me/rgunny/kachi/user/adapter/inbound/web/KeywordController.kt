package me.rgunny.kachi.user.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.inbound.web.dto.KeywordResponse
import me.rgunny.kachi.user.adapter.inbound.web.dto.RegisterKeywordRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.UpdateKeywordRequest
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordsQuery
import me.rgunny.kachi.user.application.port.inbound.keyword.ListKeywordsUseCase
import me.rgunny.kachi.user.application.port.inbound.keyword.model.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.inbound.keyword.RegisterKeywordUseCase
import me.rgunny.kachi.user.application.port.inbound.keyword.model.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.inbound.keyword.UpdateKeywordUseCase
import me.rgunny.kachi.user.domain.KeywordId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class KeywordController(
    private val registerKeywordUseCase: RegisterKeywordUseCase,
    private val updateKeywordUseCase: UpdateKeywordUseCase,
    private val listKeywordsUseCase: ListKeywordsUseCase
) {

    @GetMapping(ApiPaths.ME_KEYWORDS, version = ApiVersions.V1)
    fun listMyKeywords(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser
    ): ResponseEntity<ApiResponse<List<KeywordResponse>>> {
        val response = listKeywordsUseCase.list(ListKeywordsQuery(authenticatedUser.userId))
            .map(KeywordResponse::from)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @PostMapping(ApiPaths.ME_KEYWORDS, version = ApiVersions.V1)
    fun registerMyKeyword(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser,
        @Valid @RequestBody request: RegisterKeywordRequest
    ): ResponseEntity<ApiResponse<KeywordResponse>> {
        val result = registerKeywordUseCase.register(
            RegisterKeywordCommand(
                userId = authenticatedUser.userId,
                name = request.name
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(KeywordResponse.from(result)))
    }

    @PatchMapping(ApiPaths.KEYWORDS, version = ApiVersions.V1)
    fun update(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser,
        @PathVariable keywordId: UUID,
        @Valid @RequestBody request: UpdateKeywordRequest
    ): ResponseEntity<ApiResponse<KeywordResponse>> {
        val result = updateKeywordUseCase.update(
            UpdateKeywordCommand(
                keywordId = KeywordId.of(keywordId),
                userId = authenticatedUser.userId,
                name = request.name,
                enabled = request.enabled
            )
        )

        return ResponseEntity.ok(ApiResponse.success(KeywordResponse.from(result)))
    }
}
