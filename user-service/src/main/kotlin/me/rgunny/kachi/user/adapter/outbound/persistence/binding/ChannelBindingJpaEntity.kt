package me.rgunny.kachi.user.adapter.outbound.persistence.binding

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.user.application.port.outbound.binding.AddressCipherPort
import me.rgunny.kachi.user.domain.ChannelAddress
import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.LinkTokenHash
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * `channel_bindings` 테이블.
 *
 * 주소는 암호문으로만 두고 어느 키로 만들었는지 [keyVersion]에 남긴다.
 * 도메인과의 변환에는 cipher가 필요하다.
 */
@Entity
@Table(
    name = "channel_bindings",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_channel_bindings_user_id_channel", columnNames = ["user_id", "channel"]),
        UniqueConstraint(name = "uk_channel_bindings_link_token_hash", columnNames = ["link_token_hash"])
    ]
)
class ChannelBindingJpaEntity(

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    val id: UUID,

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    val channel: SubscriptionChannel,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: ChannelBindingStatus,

    @Column(name = "address_ciphertext", columnDefinition = "VARBINARY(1024)")
    val addressCiphertext: ByteArray?,

    @Column(name = "key_version", nullable = false)
    val keyVersion: Int,

    @Column(name = "link_token_hash", columnDefinition = "BINARY(32)")
    val linkTokenHash: ByteArray?,

    @Column(name = "link_token_expires_at")
    val linkTokenExpiresAt: Instant?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "bound_at")
    val boundAt: Instant?,

    @Column(name = "revoked_at")
    val revokedAt: Instant?
) {

    companion object {
        fun from(binding: ChannelBinding, cipher: AddressCipherPort): ChannelBindingJpaEntity {
            return ChannelBindingJpaEntity(
                id = binding.id.value,
                userId = binding.userId.value,
                channel = binding.channel,
                status = binding.status,
                addressCiphertext = binding.address?.let { cipher.encrypt(it.value) },
                keyVersion = cipher.keyVersion,
                linkTokenHash = binding.linkTokenHash?.value,
                linkTokenExpiresAt = binding.linkTokenExpiresAt,
                createdAt = binding.createdAt,
                boundAt = binding.boundAt,
                revokedAt = binding.revokedAt
            )
        }
    }

    fun toDomain(cipher: AddressCipherPort): ChannelBinding {
        return ChannelBinding.restore(
            id = ChannelBindingId.of(id),
            userId = UserId.of(userId),
            channel = channel,
            status = status,
            address = addressCiphertext?.let { ChannelAddress.of(channel, cipher.decrypt(it)) },
            linkTokenHash = linkTokenHash?.let(LinkTokenHash::restore),
            linkTokenExpiresAt = linkTokenExpiresAt,
            createdAt = createdAt,
            boundAt = boundAt,
            revokedAt = revokedAt
        )
    }
}
