package me.rgunny.kachi.user.adapter.`in`.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.`in`.web.dto.RefreshTokenRequest
import me.rgunny.kachi.user.adapter.`in`.web.dto.TokenResponse
import me.rgunny.kachi.user.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val refreshTokenUseCase: RefreshTokenUseCase
) {

    @PostMapping(ApiPaths.AUTH_TOKEN_REFRESH, version = ApiVersions.V1)
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val result = refreshTokenUseCase.refresh(RefreshTokenCommand(request.refreshToken))

        return ResponseEntity.ok(ApiResponse.success(TokenResponse.from(result)))
    }
}
