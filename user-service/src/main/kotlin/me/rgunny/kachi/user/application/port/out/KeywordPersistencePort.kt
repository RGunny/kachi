package me.rgunny.kachi.user.application.port.out

import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId

/** 관심 키워드 저장소 접근을 application 계층에 제공하는 출력 포트 */
interface KeywordPersistencePort {
    fun findById(keywordId: KeywordId): Keyword?

    fun findAllByUserId(userId: UserId): List<Keyword>

    fun existsByUserIdAndName(userId: UserId, name: KeywordName): Boolean

    fun save(keyword: Keyword): Keyword
}
