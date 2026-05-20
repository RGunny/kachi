package me.rgunny.kachi.user.application.port.out

import me.rgunny.kachi.user.application.token.IssuedToken
import me.rgunny.kachi.user.application.token.ParsedToken
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole

/** 토큰 생성과 검증 기능을 application 계층에 제공하는 출력 포트 */
interface TokenPort {

    fun issueAccessToken(userId: UserId, role: UserRole): IssuedToken

    fun issueRefreshToken(userId: UserId): IssuedToken

    fun parseToken(token: String): ParsedToken

    fun isValid(token: String): Boolean
}
