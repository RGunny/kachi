package me.rgunny.kachi.user.application.port.`in`

/** 인증 사용자를 탈퇴 상태로 변경하는 유스케이스 포트 */
interface DeactivateUserUseCase {

    fun deactivate(command: DeactivateUserCommand)
}
