package me.rgunny.kachi.collector.domain

import java.time.Instant

class News private constructor(
    val id: NewsId,
    val source: NewsSource,
    val title: NewsTitle,
    val url: NewsUrl,
    val urlHash: String,
    val titleFingerprint: String,
    val publishedAt: Instant?,
    val collectedAt: Instant,
    val matchedKeywords: List<CollectedKeyword>
) {
    companion object {

        fun create(
            source: NewsSource,
            title: NewsTitle,
            url: NewsUrl,
            publishedAt: Instant?,
            collectedAt: Instant,
            matchedKeywords: List<CollectedKeyword>
        ): News {
            require(matchedKeywords.isNotEmpty()) { "매칭 키워드는 하나 이상이어야 합니다" }

            return News(
                id = NewsId.newId(),
                source = source,
                title = title,
                url = url,
                urlHash = url.hash,
                titleFingerprint = title.fingerprint,
                publishedAt = publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = matchedKeywords.distinct()
            )
        }

        fun restore(
            id: NewsId,
            source: NewsSource,
            title: NewsTitle,
            url: NewsUrl,
            urlHash: String,
            titleFingerprint: String,
            publishedAt: Instant?,
            collectedAt: Instant,
            matchedKeywords: List<CollectedKeyword>
        ): News {
            return News(
                id = id,
                source = source,
                title = title,
                url = url,
                urlHash = urlHash,
                titleFingerprint = titleFingerprint,
                publishedAt = publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = matchedKeywords
            )
        }
    }
}
