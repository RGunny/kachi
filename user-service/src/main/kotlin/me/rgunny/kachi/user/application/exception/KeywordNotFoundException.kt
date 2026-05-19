package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.KeywordId

class KeywordNotFoundException(
    val keywordId: KeywordId
) : RuntimeException("키워드를 찾을 수 없습니다: ${keywordId.value}")
