package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserStatus

class InactiveUserException(
    val userId: UserId,
    val status: UserStatus
) : RuntimeException("활성 사용자가 아닙니다: ${userId.value}, status=$status")
