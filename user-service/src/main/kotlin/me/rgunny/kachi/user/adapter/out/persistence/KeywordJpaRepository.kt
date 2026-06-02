package me.rgunny.kachi.user.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KeywordJpaRepository : JpaRepository<KeywordJpaEntity, UUID> {
    fun findAllByUserId(userId: UUID): List<KeywordJpaEntity>

    fun findAllByEnabledTrue(): List<KeywordJpaEntity>

    fun findByUserIdAndName(userId: UUID, name: String): KeywordJpaEntity?

    fun existsByUserIdAndName(userId: UUID, name: String): Boolean
}
