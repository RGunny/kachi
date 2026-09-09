package me.rgunny.kachi.story.domain

import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("NewsId")
class NewsIdTest {

    @Test
    @DisplayName("같은 UUID면 같은 id다")
    fun ofUuid() {
        val uuid = UUID.randomUUID()

        assertEquals(NewsId.of(uuid), NewsId.of(uuid))
        assertEquals(uuid, NewsId.of(uuid).value)
    }
}
