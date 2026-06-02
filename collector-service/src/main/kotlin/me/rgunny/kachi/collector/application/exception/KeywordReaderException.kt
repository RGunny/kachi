package me.rgunny.kachi.collector.application.exception

class KeywordReaderException(
    val errorCode: KeywordReaderErrorCode,
    detail: String? = null
) : RuntimeException(
    detail
        ?.takeIf { it.isNotBlank() }
        ?.let { "${errorCode.code} ${errorCode.message}: $it" }
        ?: "${errorCode.code} ${errorCode.message}"
)
