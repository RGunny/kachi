package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.UserId

class UserNotFoundException(
    val userId: UserId
) : RuntimeException("사용자를 찾을 수 없습니다: ${userId.value}")
