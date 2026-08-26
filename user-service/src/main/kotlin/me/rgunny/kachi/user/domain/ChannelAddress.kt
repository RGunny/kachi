package me.rgunny.kachi.user.domain

/**
 * 채널 발송 주소 평문.
 *
 * URL 주소는 그 자체가 발송 권한이라 채널별로 허용 host prefix를 검증하고, `toString()`은 마스킹한 값을 돌려줘
 * 로그에 원문이 남는 경로를 타입에서 막는다. 원문은 [value]로만 꺼낸다.
 */
class ChannelAddress private constructor(
    val channel: SubscriptionChannel,
    val value: String
) {

    companion object {
        private val SLACK_PREFIXES = listOf("https://hooks.slack.com/")
        private val DISCORD_PREFIXES = listOf("https://discord.com/api/webhooks/", "https://discordapp.com/api/webhooks/")
        private val TELEGRAM_CHAT_ID = Regex("-?[0-9]{1,20}")
        private const val MAX_LENGTH = 512
        private const val VISIBLE_SUFFIX_LENGTH = 2

        fun of(channel: SubscriptionChannel, raw: String): ChannelAddress {
            val value = raw.trim()

            require(value.isNotEmpty()) { "주소는 빈 값일 수 없습니다" }
            require(value.length <= MAX_LENGTH) { "주소는 ${MAX_LENGTH}자를 초과할 수 없습니다" }
            require(isValid(channel, value)) { invalidMessage(channel) }

            return ChannelAddress(channel, value)
        }

        fun isValid(channel: SubscriptionChannel, value: String): Boolean {
            return when (channel) {
                SubscriptionChannel.SLACK -> SLACK_PREFIXES.any { value.startsWith(it) && value.length > it.length }
                SubscriptionChannel.DISCORD -> DISCORD_PREFIXES.any { value.startsWith(it) && value.length > it.length }
                SubscriptionChannel.TELEGRAM -> TELEGRAM_CHAT_ID.matches(value)
            }
        }

        fun invalidMessage(channel: SubscriptionChannel): String {
            return when (channel) {
                SubscriptionChannel.SLACK -> "Slack 주소는 ${SLACK_PREFIXES.single()} 로 시작하는 webhook URL이어야 합니다"
                SubscriptionChannel.DISCORD -> "Discord 주소는 ${DISCORD_PREFIXES.first()} 로 시작하는 webhook URL이어야 합니다"
                SubscriptionChannel.TELEGRAM -> "Telegram 주소는 숫자 chat id여야 합니다"
            }
        }
    }

    /** URL은 host까지, 그 외는 끝 두 글자만 남긴다. 어느 주소인지 알아볼 수는 있고 복원할 수는 없는 정도다. */
    fun masked(): String {
        val pathStart = value.indexOf('/', startIndex = value.indexOf("://") + 3)

        return if (value.contains("://") && pathStart > 0) {
            value.substring(0, pathStart) + "/****"
        } else {
            "****" + value.takeLast(VISIBLE_SUFFIX_LENGTH)
        }
    }

    override fun toString(): String = "ChannelAddress(channel=$channel, value=${masked()})"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChannelAddress) return false

        return channel == other.channel && value == other.value
    }

    override fun hashCode(): Int = 31 * channel.hashCode() + value.hashCode()
}
