package me.rgunny.kachi.user.adapter.out.persistence

import me.rgunny.kachi.user.application.port.out.KeywordPersistencePort
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import org.springframework.stereotype.Repository

@Repository
class KeywordPersistenceAdapter(
    private val keywordJpaRepository: KeywordJpaRepository
) : KeywordPersistencePort {

    override fun findById(keywordId: KeywordId): Keyword? {
        return keywordJpaRepository.findById(keywordId.value)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAllByUserId(userId: UserId): List<Keyword> {
        return keywordJpaRepository.findAllByUserId(userId.value)
            .map { it.toDomain() }
    }

    override fun findAllEnabled(): List<Keyword> {
        return keywordJpaRepository.findAllByEnabledTrue()
            .map { it.toDomain() }
    }

    override fun existsByUserIdAndName(userId: UserId, name: KeywordName): Boolean {
        return keywordJpaRepository.existsByUserIdAndName(
            userId = userId.value,
            name = name.value
        )
    }

    override fun save(keyword: Keyword): Keyword {
        return keywordJpaRepository.save(KeywordJpaEntity.from(keyword)).toDomain()
    }
}
