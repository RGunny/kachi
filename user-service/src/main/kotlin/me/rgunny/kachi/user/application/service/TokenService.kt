package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.InvalidTokenException
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenResult
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenUseCase
import me.rgunny.kachi.user.application.port.out.RefreshTokenStorePort
import me.rgunny.kachi.user.application.port.out.TokenPort
import me.rgunny.kachi.user.application.token.StoredRefreshToken
import me.rgunny.kachi.user.application.token.TokenType
import org.springframework.stereotype.Service

@Service
class TokenService(
    private val tokenPort: TokenPort,
    private val refreshTokenStorePort: RefreshTokenStorePort,
    private val activeUserValidator: ActiveUserValidator
) : RefreshTokenUseCase {

    override fun refresh(command: RefreshTokenCommand): RefreshTokenResult {
        // 1. refresh token의 서명과 만료 시간을 검증한다.
        if (!tokenPort.isValid(command.refreshToken)) {
            throw InvalidTokenException()
        }

        // 2. API 인증용 access token이 아니라 재발급용 refresh token인지 확인한다.
        val claims = tokenPort.parseToken(command.refreshToken)
        if (claims.type != TokenType.REFRESH) {
            throw InvalidTokenException("refresh token이 필요합니다")
        }

        // 3. 토큰의 사용자가 여전히 활성 상태인지 확인한다.
        val user = activeUserValidator.get(claims.userId)

        // 4. 저장소에 남아 있는 refresh token만 재발급에 사용할 수 있다.
        if (!refreshTokenStorePort.exists(user.id, claims.id)) {
            throw InvalidTokenException()
        }

        // 5. 새 토큰을 발급하고 기존 refresh token을 새 refresh token으로 회전한다.
        val accessToken = tokenPort.issueAccessToken(user.id, user.role)
        val refreshToken = tokenPort.issueRefreshToken(user.id)
        val rotated = refreshTokenStorePort.rotate(
            userId = user.id,
            oldTokenId = claims.id,
            newToken = StoredRefreshToken(
                id = refreshToken.id,
                userId = user.id,
                expiresAt = refreshToken.expiresAt
            )
        )

        if (!rotated) {
            throw InvalidTokenException()
        }

        return RefreshTokenResult(
            accessToken = accessToken.value,
            accessTokenExpiresAt = accessToken.expiresAt,
            refreshToken = refreshToken.value,
            refreshTokenExpiresAt = refreshToken.expiresAt
        )
    }
}
