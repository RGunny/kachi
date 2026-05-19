package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.Email

class DuplicateEmailException(
    val email: Email
) : RuntimeException("이미 사용 중인 이메일입니다: ${email.value}")
