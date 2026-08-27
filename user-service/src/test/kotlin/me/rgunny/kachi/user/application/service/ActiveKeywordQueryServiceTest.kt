package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.service.fake.FakeKeywordPersistencePort
import me.rgunny.kachi.user.fixture.UserTestFixture
import me.rgunny.kachi.user.fixture.UserTestFixture.keyword
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("ActiveKeywordQueryService")
class ActiveKeywordQueryServiceTest {
    private val now = UserTestFixture.NOW

    @Nested
    @DisplayName("listActiveKeywords()")
    inner class ListActiveKeywords {

        @Test
        @DisplayName("enabled 구독이 있는 키워드만 canonicalKey 순으로 조회한다")
        fun listActiveKeywords() {
            val tesla = keyword("Tesla")
            val nvidia = keyword("NVIDIA")
            val orphan = keyword("Bitcoin")
            val service = ActiveKeywordQueryService(
                FakeKeywordPersistencePort(
                    keywords = listOf(tesla, nvidia, orphan),
                    keywordIdsWithEnabledSubscription = setOf(tesla.id, nvidia.id)
                )
            )

            val results = service.listActiveKeywords()

            assertEquals(listOf("nvidia", "tesla"), results.map { it.canonicalKey })
            assertEquals(listOf("NVIDIA", "Tesla"), results.map { it.displayName })
            assertEquals(listOf(nvidia.id, tesla.id), results.map { it.keywordId })
        }
    }
}
