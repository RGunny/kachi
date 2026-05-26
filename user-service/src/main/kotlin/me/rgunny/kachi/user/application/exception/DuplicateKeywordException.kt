package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId

class DuplicateKeywordException(
    val userId: UserId,
    val name: KeywordName
) : RuntimeException("이미 등록된 키워드입니다: ${name.value}")
