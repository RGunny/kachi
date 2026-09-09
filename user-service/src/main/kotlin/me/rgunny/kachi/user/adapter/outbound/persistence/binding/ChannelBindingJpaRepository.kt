package me.rgunny.kachi.user.adapter.outbound.persistence.binding

import java.util.UUID
import me.rgunny.kachi.user.domain.SubscriptionChannel
import org.springframework.data.jpa.repository.JpaRepository

/**
 * `channel_bindings` 테이블 Spring Data 저장소.
 */
interface ChannelBindingJpaRepository : JpaRepository<ChannelBindingJpaEntity, UUID> {
    fun findByUserIdAndChannel(userId: UUID, channel: SubscriptionChannel): ChannelBindingJpaEntity?

    fun findAllByUserId(userId: UUID): List<ChannelBindingJpaEntity>

    fun findAllByUserIdIn(userIds: Collection<UUID>): List<ChannelBindingJpaEntity>

    fun findByLinkTokenHash(linkTokenHash: ByteArray): ChannelBindingJpaEntity?
}
