package me.rgunny.kachi.story.domain

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryId")
class StoryIdTest {

    @Test
    @DisplayName("새 id는 UUID v7이고 서로 다르다")
    fun newIdIsUuidV7() {
        val first = StoryId.newId()
        val second = StoryId.newId()

        assertEquals(7, first.value.version())
        assertNotEquals(first, second)
    }

    @Test
    @DisplayName("같은 UUID면 같은 id다")
    fun ofUuid() {
        val uuid = UUID.randomUUID()

        assertEquals(StoryId.of(uuid), StoryId.of(uuid))
    }
}
