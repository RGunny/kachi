package me.rgunny.kachi.story.fake

import java.time.Instant
import me.rgunny.kachi.story.application.port.outbound.index.CandidateIndexPort
import me.rgunny.kachi.story.application.port.outbound.index.model.CandidateHit
import me.rgunny.kachi.story.application.port.outbound.index.model.CandidateQuery
import me.rgunny.kachi.story.application.port.outbound.index.model.IndexedArticle
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId

/**
 * 메모리 map으로 후보 색인을 흉내 내는 포트.
 *
 * 검색은 코사인으로 정렬한다.
 */
class InMemoryCandidateIndexPort : CandidateIndexPort {

    val points: MutableMap<NewsId, IndexedArticle> = linkedMapOf()

    override suspend fun upsert(articles: List<IndexedArticle>) {
        articles.forEach { points[it.newsId] = it }
    }

    override suspend fun search(query: CandidateQuery): List<CandidateHit> {
        return points.values
            .asSequence()
            .filter { !it.collectedAt.isBefore(query.collectedAfter) }
            .filter { query.language == null || it.language == query.language }
            .map { CandidateHit(newsId = it.newsId, storyId = it.storyId, similarity = query.embedding.cosine(it.embedding)) }
            .sortedByDescending { it.similarity }
            .take(query.limit)
            .toList()
    }

    override suspend fun deleteByStory(storyId: StoryId) {
        points.values.removeIf { it.storyId == storyId }
    }

    override suspend fun reassignStory(from: StoryId, to: StoryId) {
        points.replaceAll { _, article -> if (article.storyId == from) article.copy(storyId = to) else article }
    }

    override suspend fun deleteByNewsIds(newsIds: List<NewsId>) {
        newsIds.forEach { points.remove(it) }
    }

    override suspend fun deleteCollectedBefore(threshold: Instant) {
        points.values.removeIf { it.collectedAt.isBefore(threshold) }
    }
}
