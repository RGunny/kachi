package me.rgunny.kachi.story.application.service.assembly

import me.rgunny.kachi.story.domain.LinkDecision
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId

/**
 * 판정이 정한 기사의 자리.
 *
 * [target]이 있으면 그 story에 붙이고, 없으면 [parentStoryId]를 이은 새 story를 연다.
 */
class ArticlePlacement(
    val decision: LinkDecision,
    val target: Story?,
    val parentStoryId: StoryId?
) {
    init {
        require(target == null || parentStoryId == null) { "붙일 story가 있으면 부모 story는 없어야 합니다" }
        require(decision.merged == (target != null)) { "병합 판정과 붙일 story의 유무가 다릅니다: merged=${decision.merged}" }
    }
}
