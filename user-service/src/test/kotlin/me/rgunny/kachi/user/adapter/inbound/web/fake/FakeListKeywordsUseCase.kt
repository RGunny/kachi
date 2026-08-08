package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordsQuery
import me.rgunny.kachi.user.application.port.inbound.keyword.ListKeywordsUseCase
import me.rgunny.kachi.user.domain.KeywordId
import java.time.Instant
import me.rgunny.kachi.user.fixture.UserTestFixture

class FakeListKeywordsUseCase : ListKeywordsUseCase {
    var exception: RuntimeException? = null

    override fun list(query: ListKeywordsQuery): List<ListKeywordResult> {
        exception?.let { throw it }

        return listOf(
            ListKeywordResult(
                id = KeywordId.newId(),
                userId = query.userId,
                name = "Trump",
                enabled = true,
                registeredAt = REGISTERED_AT,
                disabledAt = null
            )
        )
    }

    companion object {
        private val REGISTERED_AT: Instant = UserTestFixture.NOW
    }
}
