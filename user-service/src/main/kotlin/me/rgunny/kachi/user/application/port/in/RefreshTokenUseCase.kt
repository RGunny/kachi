package me.rgunny.kachi.user.application.port.`in`

/** refresh token으로 access/refresh token 갱신 유스케이스를 외부 입력 어댑터에 제공하는 포트 */
interface RefreshTokenUseCase {

    fun refresh(command: RefreshTokenCommand): RefreshTokenResult
}
