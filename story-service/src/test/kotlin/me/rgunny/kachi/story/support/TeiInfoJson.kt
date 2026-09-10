package me.rgunny.kachi.story.support

import org.springframework.core.io.ClassPathResource

/** 고정 이미지의 실제 `/info` 응답. */
object TeiInfoJson {
    val EMBEDDING: String = read("tei/info-embedding.json")
    val RERANKER: String = read("tei/info-reranker.json")

    private fun read(path: String): String {
        return ClassPathResource(path).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
