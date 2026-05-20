package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId

class KeywordAccessDeniedException(
    val keywordId: KeywordId,
    val userId: UserId
) : RuntimeException("키워드에 접근할 수 없습니다: keywordId=${keywordId.value}, userId=${userId.value}")
