package me.rgunny.kachi.ai.application.port.inbound.story.model

/**
 * maxWait를 넘긴 story들을 요약하라는 명령.
 *
 * [maxStories]는 tick 하나가 요약하는 story 수 상한이다.
 */
data class SummarizeDueStoriesCommand(
    val maxStories: Int
) {
    init {
        require(maxStories in 1..MAX_STORIES_PER_TICK) {
            "tick당 최대 story 수는 1 이상 $MAX_STORIES_PER_TICK 이하여야 합니다: $maxStories"
        }
    }

    companion object {
        const val MAX_STORIES_PER_TICK = 200
    }
}
