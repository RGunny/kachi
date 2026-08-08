package me.rgunny.kachi.user.application.port.inbound.auth

import me.rgunny.kachi.user.application.port.inbound.auth.model.ResolveOAuthUserCommand
import me.rgunny.kachi.user.application.port.inbound.auth.model.ResolveOAuthUserResult

/**
 * OAuth2 provider 사용자 정보로 기존 User를 찾고, 없으면 신규 User를 등록하는 유스케이스 포트
 */
interface ResolveOAuthUserUseCase {

    fun resolve(command: ResolveOAuthUserCommand): ResolveOAuthUserResult
}
