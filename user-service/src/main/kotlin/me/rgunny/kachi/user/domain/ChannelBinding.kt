package me.rgunny.kachi.user.domain

import java.time.Instant

/**
 * 사용자의 채널별 수신처.
 *
 * 사용자·채널당 하나이며 재등록·해지·재연결은 모두 같은 행의 상태 전이다.
 * 그래서 [id]가 수신처 참조로 밖에 나가도 주소가 바뀌는 동안 참조가 끊기지 않는다.
 * 주소는 ACTIVE일 때만 있고 해지하면 지운다.
 * 사용자가 주소를 직접 줄 수 없는 채널은 연결 토큰을 발급해 PENDING으로 두고, 그 토큰이 주소와 함께 돌아올 때 ACTIVE가 된다.
 */
class ChannelBinding private constructor(
    val id: ChannelBindingId,
    val userId: UserId,
    val channel: SubscriptionChannel,
    val status: ChannelBindingStatus,
    val address: ChannelAddress?,
    val linkTokenHash: LinkTokenHash?,
    val linkTokenExpiresAt: Instant?,
    val createdAt: Instant,
    val boundAt: Instant?,
    val revokedAt: Instant?
) {
    init {
        require(address == null || address.channel == channel) { "주소의 채널이 바인딩의 채널과 다릅니다" }
        require((status == ChannelBindingStatus.ACTIVE) == (address != null)) { "ACTIVE 바인딩만 주소를 가집니다" }
        require((status == ChannelBindingStatus.PENDING) == (linkTokenHash != null)) { "PENDING 바인딩만 연결 토큰을 가집니다" }
        require((linkTokenHash == null) == (linkTokenExpiresAt == null)) { "연결 토큰과 만료 시각은 함께 있어야 합니다" }
    }

    companion object {

        /** 주소를 직접 받는 바인딩. 등록 즉시 ACTIVE다. */
        fun createWithAddress(
            userId: UserId,
            address: ChannelAddress,
            createdAt: Instant
        ): ChannelBinding {
            return ChannelBinding(
                id = ChannelBindingId.newId(),
                userId = userId,
                channel = address.channel,
                status = ChannelBindingStatus.ACTIVE,
                address = address,
                linkTokenHash = null,
                linkTokenExpiresAt = null,
                createdAt = createdAt,
                boundAt = createdAt,
                revokedAt = null
            )
        }

        /** 연결 토큰으로 주소를 나중에 받는 바인딩. 토큰이 돌아올 때까지 PENDING이다. */
        fun createPending(
            userId: UserId,
            channel: SubscriptionChannel,
            linkToken: LinkToken,
            createdAt: Instant
        ): ChannelBinding {
            return ChannelBinding(
                id = ChannelBindingId.newId(),
                userId = userId,
                channel = channel,
                status = ChannelBindingStatus.PENDING,
                address = null,
                linkTokenHash = linkToken.hash(),
                linkTokenExpiresAt = linkToken.expiresAt,
                createdAt = createdAt,
                boundAt = null,
                revokedAt = null
            )
        }

        fun restore(
            id: ChannelBindingId,
            userId: UserId,
            channel: SubscriptionChannel,
            status: ChannelBindingStatus,
            address: ChannelAddress?,
            linkTokenHash: LinkTokenHash?,
            linkTokenExpiresAt: Instant?,
            createdAt: Instant,
            boundAt: Instant?,
            revokedAt: Instant?
        ): ChannelBinding {
            return ChannelBinding(
                id = id,
                userId = userId,
                channel = channel,
                status = status,
                address = address,
                linkTokenHash = linkTokenHash,
                linkTokenExpiresAt = linkTokenExpiresAt,
                createdAt = createdAt,
                boundAt = boundAt,
                revokedAt = revokedAt
            )
        }
    }

    val isActive: Boolean
        get() = status == ChannelBindingStatus.ACTIVE

    /** 주소를 교체하거나 해지된 바인딩을 되살린다. 어느 상태에서든 ACTIVE로 간다. */
    fun bindAddress(address: ChannelAddress, boundAt: Instant): ChannelBinding {
        require(address.channel == channel) { "주소의 채널이 바인딩의 채널과 다릅니다" }

        return copy(
            status = ChannelBindingStatus.ACTIVE,
            address = address,
            linkTokenHash = null,
            linkTokenExpiresAt = null,
            boundAt = boundAt,
            revokedAt = null
        )
    }

    /** 새 연결 토큰을 발급하고 PENDING으로 돌아간다. 이전 주소·토큰은 버린다. */
    fun issueLinkToken(linkToken: LinkToken): ChannelBinding {
        return copy(
            status = ChannelBindingStatus.PENDING,
            address = null,
            linkTokenHash = linkToken.hash(),
            linkTokenExpiresAt = linkToken.expiresAt,
            revokedAt = null
        )
    }

    fun isLinkTokenExpired(now: Instant): Boolean {
        val expiresAt = linkTokenExpiresAt ?: return true

        return !now.isBefore(expiresAt)
    }

    /** 토큰이 돌아왔다. PENDING이고 만료 전이어야 한다. */
    fun completeLink(address: ChannelAddress, boundAt: Instant): ChannelBinding {
        require(status == ChannelBindingStatus.PENDING) { "연결 대기 중인 바인딩이 아닙니다" }
        require(!isLinkTokenExpired(boundAt)) { "연결 토큰이 만료됐습니다" }

        return bindAddress(address, boundAt)
    }

    /** 주소와 토큰을 지우고 REVOKED가 된다. 이미 해지된 바인딩은 다시 해지할 수 없다. */
    fun revoke(revokedAt: Instant): ChannelBinding {
        require(status != ChannelBindingStatus.REVOKED) { "이미 해지된 바인딩입니다" }

        return copy(
            status = ChannelBindingStatus.REVOKED,
            address = null,
            linkTokenHash = null,
            linkTokenExpiresAt = null,
            revokedAt = revokedAt
        )
    }

    private fun copy(
        status: ChannelBindingStatus = this.status,
        address: ChannelAddress? = this.address,
        linkTokenHash: LinkTokenHash? = this.linkTokenHash,
        linkTokenExpiresAt: Instant? = this.linkTokenExpiresAt,
        boundAt: Instant? = this.boundAt,
        revokedAt: Instant? = this.revokedAt
    ): ChannelBinding {
        return ChannelBinding(
            id = id,
            userId = userId,
            channel = channel,
            status = status,
            address = address,
            linkTokenHash = linkTokenHash,
            linkTokenExpiresAt = linkTokenExpiresAt,
            createdAt = createdAt,
            boundAt = boundAt,
            revokedAt = revokedAt
        )
    }
}
