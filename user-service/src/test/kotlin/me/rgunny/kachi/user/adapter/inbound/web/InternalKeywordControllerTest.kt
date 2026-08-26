package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.application.port.inbound.keyword.ListActiveKeywordsUseCase
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListActiveKeywordResult
import me.rgunny.kachi.user.domain.KeywordId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

@DisplayName("InternalKeywordController")
class InternalKeywordControllerTest {

    @Test
    @DisplayName("활성 키워드 응답의 name은 canonicalKey와 같다")
    fun nameIsCanonicalKey() {
        val keywordId = KeywordId.newId()
        val controller = InternalKeywordController(
            object : ListActiveKeywordsUseCase {
                override fun listActiveKeywords() = listOf(
                    ListActiveKeywordResult(keywordId = keywordId, canonicalKey = "space-x", displayName = "SPACE-X")
                )
            }
        )

        val response = controller.listActiveKeywords()

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body?.data?.single()
        assertEquals(keywordId.value.toString(), body?.keywordId)
        assertEquals("space-x", body?.canonicalKey)
        assertEquals("space-x", body?.name)
        assertEquals("SPACE-X", body?.displayName)
    }
}
