package me.rgunny.kachi.user.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.inbound.web.dto.RegisterUserRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.UserResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.port.inbound.user.model.DeactivateUserCommand
import me.rgunny.kachi.user.application.port.inbound.user.DeactivateUserUseCase
import me.rgunny.kachi.user.application.port.inbound.user.model.GetUserQuery
import me.rgunny.kachi.user.application.port.inbound.user.GetUserUseCase
import me.rgunny.kachi.user.application.port.inbound.user.model.RegisterUserCommand
import me.rgunny.kachi.user.application.port.inbound.user.RegisterUserUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val deactivateUserUseCase: DeactivateUserUseCase
) {

    @GetMapping(ApiPaths.ME, version = ApiVersions.V1)
    fun getMe(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val result = getUserUseCase.get(GetUserQuery(authenticatedUser.userId))

        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(result)))
    }

    @PostMapping(ApiPaths.USERS, version = ApiVersions.V1)
    fun register(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<ApiResponse<UserResponse>> {
        val result = registerUserUseCase.register(
            RegisterUserCommand(
                email = request.email,
                nickname = request.nickname,
                authProvider = request.authProvider,
                providerUserId = request.providerUserId
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(UserResponse.from(result)))
    }

    @DeleteMapping(ApiPaths.ME, version = ApiVersions.V1)
    fun deactivateMe(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser
    ): ResponseEntity<ApiResponse<Unit>> {
        deactivateUserUseCase.deactivate(DeactivateUserCommand(authenticatedUser.userId))

        return ResponseEntity.ok(ApiResponse.success())
    }
}
