package me.rgunny.kachi.story.adapter.inbound.web

import me.rgunny.kachi.story.application.port.inbound.story.model.StoryDetail

/**
 * story 상세 조회 응답.
 */
data class StoryDetailResponse(
    val story: StoryResponse,
    val articles: List<StoryArticleResponse>
) {
    companion object {

        fun from(detail: StoryDetail): StoryDetailResponse {
            return StoryDetailResponse(
                story = StoryResponse.from(detail.story),
                articles = detail.articles.map(StoryArticleResponse::from)
            )
        }
    }
}
