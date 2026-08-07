package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordResult
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordUseCase
import me.rgunny.kachi.user.domain.KeywordId
import java.time.Instant
import me.rgunny.kachi.user.fixture.UserTestFixture

class FakeRegisterKeywordUseCase : RegisterKeywordUseCase {
    var exception: RuntimeException? = null

    override fun register(command: RegisterKeywordCommand): RegisterKeywordResult {
        exception?.let { throw it }

        return RegisterKeywordResult(
            id = KeywordId.newId(),
            userId = command.userId,
            name = command.name,
            enabled = true,
            registeredAt = REGISTERED_AT
        )
    }

    companion object {
        private val REGISTERED_AT: Instant = UserTestFixture.NOW
    }
}
