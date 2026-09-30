package me.rgunny.kachi.story.adapter.inbound.web

import java.util.UUID

/**
 * 분리 요청 body.
 */
data class SplitStoryRequest(
    val newsIds: List<UUID> = emptyList()
)
