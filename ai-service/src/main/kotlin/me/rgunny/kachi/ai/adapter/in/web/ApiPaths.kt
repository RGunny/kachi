package me.rgunny.kachi.ai.adapter.`in`.web

object ApiPaths {
    const val INTERNAL_LLM_PROVIDER_HEALTH = "/internal/providers/llm/health"

    const val V1_INTERNAL_LLM_PROVIDER_HEALTH = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_LLM_PROVIDER_HEALTH"
}
