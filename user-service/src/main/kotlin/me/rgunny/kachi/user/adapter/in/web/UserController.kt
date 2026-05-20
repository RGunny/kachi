package me.rgunny.kachi.user.adapter.`in`.web

import jakarta.validation.Valid
import me.rgunny.kachi.user.adapter.`in`.web.dto.RegisterUserRequest
import me.rgunny.kachi.user.adapter.`in`.web.dto.UserResponse
import me.rgunny.kachi.user.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterUserUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(ApiPaths.USERS)
class UserController(
    private val registerUserUseCase: RegisterUserUseCase
) {

    @PostMapping(version = ApiVersions.V1)
    fun register(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<ApiResponse<UserResponse>> {
        val result = registerUserUseCase.register(
            RegisterUserCommand(
                email = request.email,
                nickname = request.nickname,
                authProvider = request.authProvider
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(UserResponse.from(result)))
    }
}
