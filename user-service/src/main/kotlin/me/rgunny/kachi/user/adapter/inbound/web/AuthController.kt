package me.rgunny.kachi.user.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.inbound.web.dto.LogoutRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.RefreshTokenRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.TokenResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.inbound.auth.model.LogoutCommand
import me.rgunny.kachi.user.application.port.inbound.auth.LogoutUseCase
import me.rgunny.kachi.user.application.port.inbound.auth.model.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.inbound.auth.RefreshTokenUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val refreshTokenUseCase: RefreshTokenUseCase,
    private val logoutUseCase: LogoutUseCase
) {

    @PostMapping(ApiPaths.AUTH_TOKEN_REFRESH, version = ApiVersions.V1)
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val result = refreshTokenUseCase.refresh(RefreshTokenCommand(request.refreshToken))

        return ResponseEntity.ok(ApiResponse.success(TokenResponse.from(result)))
    }

    @PostMapping(ApiPaths.AUTH_LOGOUT, version = ApiVersions.V1)
    fun logout(
        @Valid @RequestBody request: LogoutRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        logoutUseCase.logout(LogoutCommand(request.refreshToken))

        return ResponseEntity.ok(ApiResponse.success())
    }
}
