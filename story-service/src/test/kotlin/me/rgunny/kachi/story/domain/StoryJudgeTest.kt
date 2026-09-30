package me.rgunny.kachi.story.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("StoryJudge")
class StoryJudgeTest {

    @ParameterizedTest
    @EnumSource(StoryJudge::class)
    @DisplayName("code로 되찾는다")
    fun ofCode(judge: StoryJudge) {
        assertEquals(judge, StoryJudge.ofCode(judge.code))
    }

    @Test
    @DisplayName("모르는 code는 거부한다")
    fun rejectUnknownCode() {
        assertFailsWith<IllegalArgumentException> { StoryJudge.ofCode("unknown") }
    }
}
