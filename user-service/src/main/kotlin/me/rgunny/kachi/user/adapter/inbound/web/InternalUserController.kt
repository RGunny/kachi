package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.UserChannelsResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.inbound.internal.FindUsersByRoleUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.FindUsersByRoleQuery
import me.rgunny.kachi.user.domain.UserRole
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 internal API.
 *
 * 인증 없이 열려 있으며 알림 라우팅이 역할 단위 알림의 수신자를 물을 때 호출한다.
 */
@RestController
class InternalUserController(
    private val findUsersByRoleUseCase: FindUsersByRoleUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_USERS, version = ApiVersions.V1)
    fun findUsersByRole(
        @RequestParam role: UserRole
    ): ResponseEntity<ApiResponse<List<UserChannelsResponse>>> {
        val response = findUsersByRoleUseCase.findUsersByRole(FindUsersByRoleQuery(role))
            .map(UserChannelsResponse::from)

        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
