package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.model.RegisterKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.model.UpdateKeywordResult
import java.time.Instant

data class KeywordResponse(
    val id: String,
    val userId: String,
    val name: String,
    val enabled: Boolean,
    val registeredAt: Instant,
    val disabledAt: Instant?
) {

    companion object {

        fun from(result: RegisterKeywordResult): KeywordResponse {
            return KeywordResponse(
                id = result.id.value.toString(),
                userId = result.userId.value.toString(),
                name = result.name,
                enabled = result.enabled,
                registeredAt = result.registeredAt,
                disabledAt = null
            )
        }

        fun from(result: UpdateKeywordResult): KeywordResponse {
            return KeywordResponse(
                id = result.id.value.toString(),
                userId = result.userId.value.toString(),
                name = result.name,
                enabled = result.enabled,
                registeredAt = result.registeredAt,
                disabledAt = result.disabledAt
            )
        }

        fun from(result: ListKeywordResult): KeywordResponse {
            return KeywordResponse(
                id = result.id.value.toString(),
                userId = result.userId.value.toString(),
                name = result.name,
                enabled = result.enabled,
                registeredAt = result.registeredAt,
                disabledAt = result.disabledAt
            )
        }
    }
}
