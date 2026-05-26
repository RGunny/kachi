package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("NewsId")
class NewsIdTest {

    @Test
    @DisplayName("새 뉴스 식별자를 생성한다")
    fun newId() {
        val id = NewsId.newId()

        assertNotNull(id.value)
    }

    @Test
    @DisplayName("UUID로 뉴스 식별자를 복원한다")
    fun of() {
        val value = UUID.randomUUID()

        val id = NewsId.of(value)

        assertEquals(value, id.value)
    }
}
