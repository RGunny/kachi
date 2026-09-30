package me.rgunny.kachi.story.adapter.outbound.tei.dto

/** `GET /info` 응답. */
data class TeiInfoResponse(
    val model_id: String?,
    val model_sha: String?,
    val model_dtype: String? = null,
    val model_type: TeiModelType?,
    val max_client_batch_size: Int?,
    val max_input_length: Int?,
    val max_batch_tokens: Int?,
    val auto_truncate: Boolean? = null
)
