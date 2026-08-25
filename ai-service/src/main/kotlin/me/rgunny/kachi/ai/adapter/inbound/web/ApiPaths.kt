package me.rgunny.kachi.ai.adapter.inbound.web

object ApiPaths {
    const val INTERNAL_LLM_PROVIDER_HEALTH = "/internal/providers/llm/health"
    const val INTERNAL_AI_KEYWORD_EXPANSIONS = "/internal/ai/keyword-expansions"
    const val INTERNAL_AI_NEWS_SUMMARIES = "/internal/ai/news-summaries"
    const val INTERNAL_AI_KEYWORD_QUARANTINES = "/internal/ai/keyword-quarantines"
    const val INTERNAL_AI_KEYWORD_QUARANTINE_RELEASE = "$INTERNAL_AI_KEYWORD_QUARANTINES/{keyword}/release"
    const val INTERNAL_AI_SUMMARY_WATERMARKS = "/internal/ai/summary-watermarks"
    const val INTERNAL_AI_OUTBOXES = "/internal/ai/outboxes"
    const val INTERNAL_AI_OUTBOX_RECOVER = "$INTERNAL_AI_OUTBOXES/{outboxId}/recover"

    const val V1_INTERNAL_LLM_PROVIDER_HEALTH = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_LLM_PROVIDER_HEALTH"
    const val V1_INTERNAL_AI_KEYWORD_EXPANSIONS = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_KEYWORD_EXPANSIONS"
    const val V1_INTERNAL_AI_NEWS_SUMMARIES = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_NEWS_SUMMARIES"
    const val V1_INTERNAL_AI_KEYWORD_QUARANTINES = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_KEYWORD_QUARANTINES"
    const val V1_INTERNAL_AI_KEYWORD_QUARANTINE_RELEASE =
        "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_KEYWORD_QUARANTINE_RELEASE"
    const val V1_INTERNAL_AI_SUMMARY_WATERMARKS = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_SUMMARY_WATERMARKS"
    const val V1_INTERNAL_AI_OUTBOXES = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_OUTBOXES"
    const val V1_INTERNAL_AI_OUTBOX_RECOVER = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_OUTBOX_RECOVER"
}
