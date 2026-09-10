package me.rgunny.kachi.story.adapter.outbound.tei.dto

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.rgunny.kachi.story.support.TeiInfoJson
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/** 고정 이미지가 실제로 낸 `/info` JSON으로 DTO를 검증한다. */
@DisplayName("TeiInfoResponse")
class TeiInfoResponseTest {

    @Test
    @DisplayName("임베딩 서버의 실제 응답은 embedding 종류와 cls pooling으로 읽힌다")
    fun parseEmbeddingInfo() {
        val info = JSON.readValue(TeiInfoJson.EMBEDDING, TeiInfoResponse::class.java)

        assertEquals("BAAI/bge-m3", info.model_id)
        assertEquals("5617a9f61b028005a4858fdac845db406aefb181", info.model_sha)
        assertEquals("float32", info.model_dtype)
        assertEquals("cls", assertNotNull(info.model_type?.embedding).pooling)
        assertNull(info.model_type?.reranker)
        assertEquals(32, info.max_client_batch_size)
        assertEquals(512, info.max_input_length)
        assertEquals(512, info.max_batch_tokens)
        assertEquals(true, info.auto_truncate)
    }

    @Test
    @DisplayName("판정기 서버의 실제 응답은 reranker 종류와 라벨 표로 읽힌다")
    fun parseRerankerInfo() {
        val info = JSON.readValue(TeiInfoJson.RERANKER, TeiInfoResponse::class.java)

        assertEquals("BAAI/bge-reranker-v2-m3", info.model_id)
        assertEquals("float16", info.model_dtype)
        val reranker = assertNotNull(info.model_type?.reranker)
        assertEquals(mapOf("0" to "LABEL_0"), reranker.id2label)
        assertEquals(mapOf("LABEL_0" to 0), reranker.label2id)
        assertNull(info.model_type?.embedding)
        assertEquals(32, info.max_client_batch_size)
        assertEquals(512, info.max_input_length)
    }

    @Test
    @DisplayName("모르는 필드는 무시한다")
    fun ignoreUnknownFields() {
        val info = JSON.readValue("""{"model_id":"x","model_type":{"embedding":{"pooling":"mean","extra":1}},"future":true}""", TeiInfoResponse::class.java)

        assertEquals("x", info.model_id)
        assertEquals("mean", info.model_type?.embedding?.pooling)
        assertTrue(info.max_client_batch_size == null)
    }

    private companion object {
        val JSON: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    }
}
