package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.ChannelBindingNotFoundException
import me.rgunny.kachi.user.application.exception.InvalidChannelAddressException
import me.rgunny.kachi.user.application.exception.LinkTokenInvalidException
import me.rgunny.kachi.user.application.port.inbound.binding.CompleteTelegramLinkUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.IssueTelegramLinkUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.RegisterWebhookBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.RevokeChannelBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.ChannelBindingResult
import me.rgunny.kachi.user.application.port.inbound.binding.model.CompleteTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.IssueTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.RegisterWebhookBindingCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.RevokeChannelBindingCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.TelegramLinkResult
import me.rgunny.kachi.user.application.port.outbound.binding.ChannelBindingPersistencePort
import me.rgunny.kachi.user.domain.ChannelAddress
import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.LinkToken
import me.rgunny.kachi.user.domain.LinkTokenHash
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 채널 바인딩 등록·연결·해지.
 *
 * 사용자·채널당 바인딩이 하나이므로 모든 명령은 "있으면 그 행을 전이, 없으면 새로 만든다"로 끝난다.
 * 연결 링크 방식은 토큰 원문을 사용자에게 한 번 돌려주고 해시만 남긴다.
 * 토큰이 돌아오면 해시로 바인딩을 찾아 ACTIVE로 만든다.
 */
@Transactional
class ChannelBindingCommandService(
    private val channelBindingPersistencePort: ChannelBindingPersistencePort,
    private val activeUserValidator: ActiveUserValidator,
    private val clock: Clock,
    private val linkTokenTtl: Duration,
    private val telegramBotUsername: String
) : RegisterWebhookBindingUseCase, IssueTelegramLinkUseCase, CompleteTelegramLinkUseCase, RevokeChannelBindingUseCase {

    override fun register(command: RegisterWebhookBindingCommand): ChannelBindingResult {
        require(command.channel != SubscriptionChannel.TELEGRAM) { "Telegram은 연결 링크로 등록합니다" }
        activeUserValidator.get(command.userId)

        val address = address(command.channel, command.webhookUrl)
        val now = Instant.now(clock)
        val existing = channelBindingPersistencePort.findByUserIdAndChannel(command.userId, command.channel)
        val binding = existing?.bindAddress(address, now)
            ?: ChannelBinding.createWithAddress(userId = command.userId, address = address, createdAt = now)

        return ChannelBindingResult.of(channelBindingPersistencePort.save(binding))
    }

    override fun issue(command: IssueTelegramLinkCommand): TelegramLinkResult {
        activeUserValidator.get(command.userId)

        val now = Instant.now(clock)
        val token = LinkToken.issue(now, linkTokenTtl)
        val existing = channelBindingPersistencePort.findByUserIdAndChannel(command.userId, SubscriptionChannel.TELEGRAM)
        val binding = existing?.issueLinkToken(token)
            ?: ChannelBinding.createPending(
                userId = command.userId,
                channel = SubscriptionChannel.TELEGRAM,
                linkToken = token,
                createdAt = now
            )

        channelBindingPersistencePort.save(binding)

        return TelegramLinkResult(
            linkUrl = "https://t.me/$telegramBotUsername?start=${token.value}",
            expiresAt = token.expiresAt
        )
    }

    override fun complete(command: CompleteTelegramLinkCommand) {
        // 없는 토큰과 만료된 토큰을 같은 예외로 응답해 토큰 존재 여부를 밖에서 알 수 없게 한다.
        val binding = channelBindingPersistencePort.findByLinkTokenHash(LinkTokenHash.of(command.token))
            ?: throw LinkTokenInvalidException()
        val now = Instant.now(clock)

        if (binding.isLinkTokenExpired(now)) {
            throw LinkTokenInvalidException()
        }

        val address = address(SubscriptionChannel.TELEGRAM, command.chatId)

        channelBindingPersistencePort.save(binding.completeLink(address, now))
    }

    override fun revoke(command: RevokeChannelBindingCommand) {
        val binding = channelBindingPersistencePort.findByUserIdAndChannel(command.userId, command.channel)
            ?: throw ChannelBindingNotFoundException(command.userId, command.channel)

        channelBindingPersistencePort.save(binding.revoke(Instant.now(clock)))
    }

    private fun address(channel: SubscriptionChannel, raw: String): ChannelAddress {
        val value = raw.trim()

        if (value.isEmpty() || !ChannelAddress.isValid(channel, value)) {
            throw InvalidChannelAddressException(channel, ChannelAddress.invalidMessage(channel))
        }

        return ChannelAddress.of(channel, value)
    }
}
