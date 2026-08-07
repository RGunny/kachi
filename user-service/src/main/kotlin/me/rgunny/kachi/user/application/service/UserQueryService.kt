package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.user.model.GetUserQuery
import me.rgunny.kachi.user.application.port.inbound.user.model.GetUserResult
import me.rgunny.kachi.user.application.port.inbound.user.GetUserUseCase
import org.springframework.stereotype.Service

@Service
class UserQueryService(
    private val activeUserValidator: ActiveUserValidator
) : GetUserUseCase {

    override fun get(query: GetUserQuery): GetUserResult {
        return GetUserResult.from(activeUserValidator.get(query.userId))
    }
}
