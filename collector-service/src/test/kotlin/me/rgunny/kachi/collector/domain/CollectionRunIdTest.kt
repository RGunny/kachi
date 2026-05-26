package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("CollectionRunId")
class CollectionRunIdTest {

    @Test
    @DisplayName("새 수집 실행 식별자를 생성한다")
    fun newId() {
        val id = CollectionRunId.newId()

        assertNotNull(id.value)
    }

    @Test
    @DisplayName("UUID로 수집 실행 식별자를 복원한다")
    fun of() {
        val value = UUID.randomUUID()

        val id = CollectionRunId.of(value)

        assertEquals(value, id.value)
    }
}
