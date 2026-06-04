package me.rgunny.kachi.ai.application.exception

class NewsReaderException(
    val errorCode: NewsReaderErrorCode,
    detail: String? = null
) : RuntimeException(
    detail
        ?.takeIf { it.isNotBlank() }
        ?.let { "${errorCode.code} ${errorCode.message}: $it" }
        ?: "${errorCode.code} ${errorCode.message}"
)
