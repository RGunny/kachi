package me.rgunny.kachi.ai.application.exception

class KeywordReaderException(
    val errorCode: KeywordReaderErrorCode,
    detail: String? = null
) : RuntimeException(
    detail
        ?.takeIf { it.isNotBlank() }
        ?.let { "${errorCode.code} ${errorCode.message}: $it" }
        ?: "${errorCode.code} ${errorCode.message}"
)
