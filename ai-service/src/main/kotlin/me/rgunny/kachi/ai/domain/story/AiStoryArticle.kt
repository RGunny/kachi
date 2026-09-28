package me.rgunny.kachi.ai.domain.story

import java.time.Instant
import java.util.UUID

/**
 * story에 붙은 기사의 사본.
 *
 * source·title·excerpt·url은 non-blank만 요구한다.
 * [summarizedInVersion]이 null이면 아직 어느 요약 버전에도 실리지 않은 기사다.
 */
class AiStoryArticle private constructor(
    val newsId: UUID,
    val storyId: StoryId,
    val source: String,
    val title: String,
    val excerpt: String,
    val url: String,
    val publishedAt: Instant,
    val attachedAt: Instant,
    val summarizedInVersion: Long?
) {

    val pending: Boolean
        get() = summarizedInVersion == null

    fun summarizedIn(version: Long): AiStoryArticle {
        require(pending) { "이미 요약된 기사입니다: $newsId (version=$summarizedInVersion)" }
        require(version >= 1) { "요약 버전은 1 이상이어야 합니다: $version" }

        return AiStoryArticle(
            newsId = newsId,
            storyId = storyId,
            source = source,
            title = title,
            excerpt = excerpt,
            url = url,
            publishedAt = publishedAt,
            attachedAt = attachedAt,
            summarizedInVersion = version
        )
    }

    /**
     * 흡수된 story의 미요약 기사를 흡수한 story 소속으로 옮긴다.
     */
    fun reassign(storyId: StoryId): AiStoryArticle {
        require(pending) { "요약된 기사는 소속을 옮기지 않습니다: $newsId" }

        return AiStoryArticle(
            newsId = newsId,
            storyId = storyId,
            source = source,
            title = title,
            excerpt = excerpt,
            url = url,
            publishedAt = publishedAt,
            attachedAt = attachedAt,
            summarizedInVersion = null
        )
    }

    companion object {

        fun create(
            newsId: UUID,
            storyId: StoryId,
            source: String,
            title: String,
            excerpt: String,
            url: String,
            publishedAt: Instant,
            attachedAt: Instant
        ): AiStoryArticle {
            require(source.isNotBlank()) { "기사 출처는 빈 값일 수 없습니다" }
            require(title.isNotBlank()) { "기사 제목은 빈 값일 수 없습니다" }
            require(excerpt.isNotBlank()) { "기사 발췌문은 빈 값일 수 없습니다" }
            require(url.isNotBlank()) { "기사 URL은 빈 값일 수 없습니다" }

            return AiStoryArticle(
                newsId = newsId,
                storyId = storyId,
                source = source,
                title = title.trim(),
                excerpt = excerpt.trim(),
                url = url,
                publishedAt = publishedAt,
                attachedAt = attachedAt,
                summarizedInVersion = null
            )
        }

        fun restore(
            newsId: UUID,
            storyId: StoryId,
            source: String,
            title: String,
            excerpt: String,
            url: String,
            publishedAt: Instant,
            attachedAt: Instant,
            summarizedInVersion: Long?
        ): AiStoryArticle {
            return AiStoryArticle(
                newsId = newsId,
                storyId = storyId,
                source = source,
                title = title,
                excerpt = excerpt,
                url = url,
                publishedAt = publishedAt,
                attachedAt = attachedAt,
                summarizedInVersion = summarizedInVersion
            )
        }
    }
}
