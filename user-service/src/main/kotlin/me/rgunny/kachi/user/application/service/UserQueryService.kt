package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.`in`.GetUserQuery
import me.rgunny.kachi.user.application.port.`in`.GetUserResult
import me.rgunny.kachi.user.application.port.`in`.GetUserUseCase
import org.springframework.stereotype.Service

@Service
class UserQueryService(
    private val activeUserValidator: ActiveUserValidator
) : GetUserUseCase {

    override fun get(query: GetUserQuery): GetUserResult {
        return GetUserResult.from(activeUserValidator.get(query.userId))
    }
}
