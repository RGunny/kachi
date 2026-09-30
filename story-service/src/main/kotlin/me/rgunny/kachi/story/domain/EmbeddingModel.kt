package me.rgunny.kachi.story.domain

/**
 * 기사를 벡터로 바꾸는 임베딩 모델.
 */
enum class EmbeddingModel(
    val code: String,
    val modelId: String,
    val dimension: Int
) {
    BGE_M3(
        code = "bge-m3",
        modelId = "BAAI/bge-m3",
        dimension = 1024
    );

    companion object {

        /** 저장된 code에서 모델을 되찾는다. */
        fun ofCode(code: String): EmbeddingModel {
            return entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("알 수 없는 임베딩 모델 code입니다: $code")
        }
    }
}
