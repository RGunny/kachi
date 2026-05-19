package me.rgunny.kachi.user.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KeywordJpaRepository : JpaRepository<KeywordJpaEntity, UUID> {
    fun existsByUserIdAndName(userId: UUID, name: String): Boolean
}
