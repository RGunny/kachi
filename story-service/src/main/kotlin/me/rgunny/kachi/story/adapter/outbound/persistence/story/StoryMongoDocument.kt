package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryKeyword
import me.rgunny.kachi.story.domain.StoryStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

/**
 * `stories` 컬렉션 문서.
 *
 * [centroid]는 double 배열이고 모델은 [centroidModel]에 따로 둔다.
 */
@Document(collection = "stories")
@CompoundIndexes(
    CompoundIndex(
        name = "ix_stories_status_last_article_at",
        def = "{'status': 1, 'lastArticleAt': 1}"
    ),
    CompoundIndex(
        name = "ix_stories_merged_into",
        def = "{'mergedInto': 1}"
    )
)
data class StoryMongoDocument(
    @Id
    val id: UUID,
    val status: String,
    val centroidModel: String,
    val centroid: DoubleArray,
    val articleCount: Int,
    val keywords: List<String>,
    val openedAt: Instant,
    val lastArticleAt: Instant,
    val closedAt: Instant?,
    val parentStoryId: UUID?,
    val mergedInto: UUID?,
    val version: Long
) {

    fun toDomain(): Story {
        val model = EmbeddingModel.ofCode(centroidModel)
        val values = FloatArray(centroid.size) { centroid[it].toFloat() }

        return Story.restore(
            id = StoryId.of(id),
            status = StoryStatus.valueOf(status),
            centroid = Embedding.of(model, values),
            articleCount = articleCount,
            keywords = keywords.map { StoryKeyword.of(it) }.toSet(),
            openedAt = openedAt,
            lastArticleAt = lastArticleAt,
            closedAt = closedAt,
            parentStoryId = parentStoryId?.let { StoryId.of(it) },
            mergedInto = mergedInto?.let { StoryId.of(it) },
            version = version
        )
    }

    /**
     * 전이로 바뀌는 필드 전부를 쓰는 update. id·openedAt·parentStoryId는 바뀌지 않는다.
     */
    fun toTransitionUpdate(): Update {
        return Update()
            .set(FIELD_STATUS, status)
            .set(FIELD_CENTROID_MODEL, centroidModel)
            .set(FIELD_CENTROID, centroid)
            .set(FIELD_ARTICLE_COUNT, articleCount)
            .set(FIELD_KEYWORDS, keywords)
            .set(FIELD_LAST_ARTICLE_AT, lastArticleAt)
            .set(FIELD_CLOSED_AT, closedAt)
            .set(FIELD_MERGED_INTO, mergedInto)
            .set(FIELD_VERSION, version)
    }

    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STATUS = "status"
        const val FIELD_CENTROID_MODEL = "centroidModel"
        const val FIELD_CENTROID = "centroid"
        const val FIELD_ARTICLE_COUNT = "articleCount"
        const val FIELD_KEYWORDS = "keywords"
        const val FIELD_OPENED_AT = "openedAt"
        const val FIELD_LAST_ARTICLE_AT = "lastArticleAt"
        const val FIELD_CLOSED_AT = "closedAt"
        const val FIELD_MERGED_INTO = "mergedInto"
        const val FIELD_VERSION = "version"

        fun fromDomain(story: Story): StoryMongoDocument {
            val values = story.centroid.values

            return StoryMongoDocument(
                id = story.id.value,
                status = story.status.name,
                centroidModel = story.centroid.model.code,
                centroid = DoubleArray(values.size) { values[it].toDouble() },
                articleCount = story.articleCount,
                keywords = story.keywords.map { it.value },
                openedAt = story.openedAt,
                lastArticleAt = story.lastArticleAt,
                closedAt = story.closedAt,
                parentStoryId = story.parentStoryId?.value,
                mergedInto = story.mergedInto?.value,
                version = story.version
            )
        }

        /** 저장된 version이 [expectedVersion]인 story 하나를 고르는 조건. */
        fun versionQuery(id: StoryId, expectedVersion: Long): Query {
            return Query.query(
                Criteria.where(FIELD_ID).`is`(id.value)
                    .and(FIELD_VERSION).`is`(expectedVersion)
            )
        }
    }
}
