package me.rgunny.kachi.notification.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("NotificationOrigin")
class NotificationOriginTest {

    @Test
    @DisplayName("NONE은 세 필드가 모두 null이다")
    fun none() {
        assertNull(NotificationOrigin.NONE.summaryId)
        assertNull(NotificationOrigin.NONE.keyword)
        assertNull(NotificationOrigin.NONE.userId)
        assertEquals(NotificationOrigin(null, null, null), NotificationOrigin.NONE)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    @DisplayName("빈 문자열 필드는 만들 수 없다")
    fun rejectBlank(blank: String) {
        assertFailsWith<IllegalArgumentException> { NotificationOrigin(summaryId = blank, keyword = null, userId = null) }
        assertFailsWith<IllegalArgumentException> { NotificationOrigin(summaryId = null, keyword = blank, userId = null) }
        assertFailsWith<IllegalArgumentException> { NotificationOrigin(summaryId = null, keyword = null, userId = blank) }
    }
}
