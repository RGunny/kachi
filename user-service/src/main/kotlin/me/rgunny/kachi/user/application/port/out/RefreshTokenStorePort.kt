package me.rgunny.kachi.user.application.port.out

import me.rgunny.kachi.user.application.token.StoredRefreshToken
import me.rgunny.kachi.user.domain.UserId

/**
 * refresh token 저장, 검증, 회전 기능을 application 계층에 제공하는 출력 포트
 */
interface RefreshTokenStorePort {

    fun save(token: StoredRefreshToken)

    fun exists(userId: UserId, tokenId: String): Boolean

    fun rotate(userId: UserId, oldTokenId: String, newToken: StoredRefreshToken): Boolean

    fun revoke(userId: UserId, tokenId: String)
}
