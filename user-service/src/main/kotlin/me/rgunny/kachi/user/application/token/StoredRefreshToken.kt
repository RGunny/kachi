package me.rgunny.kachi.user.application.token

import me.rgunny.kachi.user.domain.UserId
import java.time.Instant

data class StoredRefreshToken(
    val id: String,
    val userId: UserId,
    val expiresAt: Instant
)
