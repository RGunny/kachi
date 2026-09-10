package me.rgunny.kachi.story.adapter.outbound.tei

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiEmbeddingType
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiModelType
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.domain.inference.InferenceFailure
import me.rgunny.kachi.story.domain.inference.InferenceFailureCode
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import me.rgunny.kachi.story.fake.FakeTeiClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TeiStartupProbe")
class TeiStartupProbeTest {

    @Test
    @DisplayName("임베딩 서버의 실제 /info는 모델·종류·pooling 검사를 통과한다")
    fun verifyEmbedding() = runBlocking {
        val info = TeiStartupProbe(FakeTeiClient(), InferenceTarget.EMBEDDING, "BAAI/bge-m3").verify()

        assertEquals(32, info.max_client_batch_size)
    }

    @Test
    @DisplayName("판정기 서버의 실제 /info는 모델·종류 검사를 통과한다")
    fun verifyJudge() = runBlocking {
        val client = FakeTeiClient(info = FakeTeiClient.rerankerInfo())

        val info = TeiStartupProbe(client, InferenceTarget.JUDGE, "BAAI/bge-reranker-v2-m3").verify()

        assertEquals("float16", info.model_dtype)
    }

    @Test
    @DisplayName("model_id가 다르면 실패한다")
    fun failOnDifferentModel() = runBlocking {
        val error = assertFailsWith<IllegalStateException> {
            TeiStartupProbe(FakeTeiClient(), InferenceTarget.EMBEDDING, "BAAI/bge-large").verify()
        }

        assertTrue(error.message!!.contains("expected=BAAI/bge-large, actual=BAAI/bge-m3"))
    }

    @Test
    @DisplayName("임베딩 자리에 판정기 모델이 떠 있으면 실패한다")
    fun failOnWrongKind() = runBlocking {
        val client = FakeTeiClient(info = FakeTeiClient.rerankerInfo())

        assertFailsWith<IllegalStateException> {
            TeiStartupProbe(client, InferenceTarget.EMBEDDING, "BAAI/bge-reranker-v2-m3").verify()
        }
        Unit
    }

    @Test
    @DisplayName("임베딩 pooling이 cls가 아니면 실패한다")
    fun failOnWrongPooling() = runBlocking {
        val client = FakeTeiClient(
            info = FakeTeiClient.embeddingInfo().copy(model_type = TeiModelType(embedding = TeiEmbeddingType(pooling = "mean")))
        )

        val error = assertFailsWith<IllegalStateException> {
            TeiStartupProbe(client, InferenceTarget.EMBEDDING, "BAAI/bge-m3").verify()
        }

        assertTrue(error.message!!.contains("pooling"))
    }

    @Test
    @DisplayName("판정기 자리에 임베딩 모델이 떠 있으면 실패한다")
    fun failOnJudgeWithoutReranker() = runBlocking {
        assertFailsWith<IllegalStateException> {
            TeiStartupProbe(FakeTeiClient(), InferenceTarget.JUDGE, "BAAI/bge-m3").verify()
        }
        Unit
    }

    @Test
    @DisplayName("서버에 닿지 않으면 호출 실패가 그대로 올라온다")
    fun propagateUnreachable() = runBlocking {
        val client = FakeTeiClient()
        client.failures += InferenceException(InferenceFailure(InferenceFailureCode.INFERENCE_NETWORK_ERROR, InferenceTarget.EMBEDDING))

        assertFailsWith<InferenceException> {
            TeiStartupProbe(client, InferenceTarget.EMBEDDING, "BAAI/bge-m3").verify()
        }
        Unit
    }
}
