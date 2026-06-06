package me.rgunny.kachi.ai.adapter.`in`.web

object ApiPaths {
    const val INTERNAL_LLM_PROVIDER_HEALTH = "/internal/providers/llm/health"
    const val INTERNAL_AI_KEYWORD_EXPANSIONS = "/internal/ai/keyword-expansions"
    const val INTERNAL_AI_NEWS_SUMMARIES = "/internal/ai/news-summaries"

    const val V1_INTERNAL_LLM_PROVIDER_HEALTH = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_LLM_PROVIDER_HEALTH"
    const val V1_INTERNAL_AI_KEYWORD_EXPANSIONS = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_KEYWORD_EXPANSIONS"
    const val V1_INTERNAL_AI_NEWS_SUMMARIES = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_AI_NEWS_SUMMARIES"
}
