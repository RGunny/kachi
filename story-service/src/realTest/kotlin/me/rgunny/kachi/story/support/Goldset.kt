package me.rgunny.kachi.story.support

import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * 골드셋의 기사 하나.
 *
 * 발췌문이 제목과 같으면 제목만 입력으로 쓴다.
 */
data class GoldsetArticle(
    val newsId: String,
    val title: String,
    val excerpt: String?,
    val source: String?,
    val language: String?
) {
    val hasExcerpt: Boolean
        get() = !excerpt.isNullOrBlank() && excerpt != title

    val embeddingText: String
        get() = if (hasExcerpt) "$title\n$excerpt" else title
}

/** 골드셋의 한 행. */
data class GoldsetPair(
    val pairId: String,
    val stratum: String,
    val a: GoldsetArticle,
    val b: GoldsetArticle,
    val label: Boolean?
)

/**
 * 실험이 DJL로 낸 점수 한 행.
 *
 * cosine은 -1~1, judge는 sigmoid를 거친 0~1이다.
 */
data class GoldsetScore(
    val pairId: String,
    val cosine: Double,
    val judge: Double,
    val embeddingModel: String,
    val judgeModel: String
)

/** `src/realTest/resources/goldset/`의 쌍과 점수를 읽는다. */
object Goldset {
    private val JSON: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    fun pairs(): List<GoldsetPair> = readLines("goldset/pairs.jsonl").map { JSON.readValue(it, GoldsetPair::class.java) }

    fun scores(): Map<String, GoldsetScore> {
        return readLines("goldset/scores.jsonl").map { JSON.readValue(it, GoldsetScore::class.java) }.associateBy { it.pairId }
    }

    private fun readLines(path: String): List<String> {
        return ClassPathResource(path).inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }.toList()
        }
    }
}
