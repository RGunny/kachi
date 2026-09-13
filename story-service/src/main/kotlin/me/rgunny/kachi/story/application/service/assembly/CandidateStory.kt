package me.rgunny.kachi.story.application.service.assembly

import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle

/**
 * 후보 검색이 돌려준 story 하나와 새 기사에 대한 점수.
 *
 * [score]는 centroid 유사도와 비교 기사 유사도 중 큰 값이고, [bestArticle]은 그 최대를 낸 기사다.
 */
class CandidateStory(
    val story: Story,
    val score: Double,
    val bestArticle: StoryArticle
)
