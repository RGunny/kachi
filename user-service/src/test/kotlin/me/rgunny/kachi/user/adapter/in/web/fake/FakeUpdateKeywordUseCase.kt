package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordResult
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordUseCase
import java.time.Instant

class FakeUpdateKeywordUseCase : UpdateKeywordUseCase {
    var exception: RuntimeException? = null

    override fun update(command: UpdateKeywordCommand): UpdateKeywordResult {
        exception?.let { throw it }

        return UpdateKeywordResult(
            id = command.keywordId,
            userId = command.userId,
            name = command.name ?: "Trump",
            enabled = command.enabled ?: true,
            registeredAt = REGISTERED_AT,
            disabledAt = null
        )
    }

    companion object {
        private val REGISTERED_AT: Instant = Instant.parse("2026-05-20T00:00:00Z")
    }
}
