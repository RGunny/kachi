package me.rgunny.kachi.user.application.port.inbound.auth

import me.rgunny.kachi.user.application.port.inbound.auth.model.LogoutCommand

/** refresh token을 폐기해 추가 토큰 재발급을 막는 로그아웃 유스케이스 포트 */
interface LogoutUseCase {

    fun logout(command: LogoutCommand)
}
