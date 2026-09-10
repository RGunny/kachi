package me.rgunny.kachi.story.application.port.outbound.judge

import me.rgunny.kachi.story.application.port.outbound.judge.model.JudgeCandidate
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryJudge

/**
 * 기사 하나와 후보 기사들이 같은 사건인지 채점하는 출력 포트.
 */
interface StoryLinkJudge {

    val judge: StoryJudge

    /**
     * [candidates] 순서대로 0~1 점수를 돌려준다.
     *
     * 후보가 비면 빈 목록이다.
     */
    suspend fun score(subject: EmbeddingText, candidates: List<JudgeCandidate>): List<Double>
}
