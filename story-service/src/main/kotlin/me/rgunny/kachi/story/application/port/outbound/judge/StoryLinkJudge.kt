package me.rgunny.kachi.story.application.port.outbound.judge

import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryJudge

/**
 * 기사 하나와 후보 기사들이 같은 사건인지 채점하는 출력 포트.
 */
interface StoryLinkJudge {

    val judge: StoryJudge

    suspend fun score(subject: EmbeddingText, candidates: List<EmbeddingText>): List<Double>
}
