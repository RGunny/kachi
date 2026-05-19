package me.rgunny.kachi.user.application.port.out

import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.User

/** 사용자 저장소 접근을 application 계층에 제공하는 출력 포트 */
interface UserPersistencePort {

    fun existsByEmail(email: Email): Boolean

    fun save(user: User): User
}
