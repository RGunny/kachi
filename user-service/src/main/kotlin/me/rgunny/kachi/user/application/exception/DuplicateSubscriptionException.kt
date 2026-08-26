package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId

/**
 * 같은 사용자가 같은 canonical 키워드를 다시 구독했다.
 * [displayName]은 기존 키워드의 원문이다.
 */
class DuplicateSubscriptionException(
    val userId: UserId,
    val displayName: KeywordName
) : RuntimeException("이미 구독 중인 키워드입니다: ${displayName.value}")
