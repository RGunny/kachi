package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.RecipientUnavailableReason
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import tools.jackson.databind.json.JsonMapper

/**
 * 캐시에 넣을 조회 결과와 JSON 문자열을 서로 바꾼다.
 *
 * 형식은 `{"available":true,"address":"..."}` 또는 `{"available":false,"reason":"REVOKED"}`다.
 */
class ResolvedRecipientCacheCodec(
    private val jsonMapper: JsonMapper,
) {

    fun encode(resolved: ResolvedRecipient): String {
        val entry = when (resolved) {
            is AvailableRecipient -> CachedResolvedRecipient(available = true, address = resolved.address, reason = null)
            is UnavailableRecipient -> CachedResolvedRecipient(available = false, address = null, reason = resolved.reason.name)
        }
        return jsonMapper.writeValueAsString(entry)
    }

    fun decode(value: String): ResolvedRecipient {
        val entry = jsonMapper.readValue(value, CachedResolvedRecipient::class.java)
        return if (entry.available) {
            AvailableRecipient(requireNotNull(entry.address) { "cached available recipient must have address" })
        } else {
            UnavailableRecipient(RecipientUnavailableReason.valueOf(requireNotNull(entry.reason) { "cached unavailable recipient must have reason" }))
        }
    }
}
