package me.rgunny.kachi.story.adapter.inbound.web

import me.rgunny.kachi.story.adapter.inbound.index.AlreadyRunningIndexRebuildExecution
import me.rgunny.kachi.story.adapter.inbound.index.IndexRebuildExecutor
import me.rgunny.kachi.story.adapter.inbound.index.StartedIndexRebuildExecution
import me.rgunny.kachi.story.adapter.inbound.index.UnavailableIndexRebuildExecution
import me.rgunny.kachi.story.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.story.adapter.inbound.web.response.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 후보 색인 재구축 API.
 *
 * 재구축 본체는 요청 밖에서 돌아 응답은 시작 여부만 담는다.
 */
@RestController
class InternalStoryIndexController(
    private val executor: IndexRebuildExecutor
) {

    @PostMapping(ApiPaths.INTERNAL_STORY_INDEX_REBUILD, version = ApiVersions.V1)
    suspend fun rebuildIndex(): ResponseEntity<ApiResponse<*>> {
        return when (val execution = executor.start()) {
            is StartedIndexRebuildExecution -> {
                log.info("Manual index rebuild started")

                ResponseEntity.ok(ApiResponse.success(IndexRebuildResponse(started = true)))
            }

            is AlreadyRunningIndexRebuildExecution -> {
                log.info(
                    "Manual index rebuild skipped because another rebuild is running: acquiredAt={}",
                    execution.holder.acquiredAt
                )

                ResponseEntity.status(ErrorCode.INDEX_REBUILD_ALREADY_RUNNING.status)
                    .body(ApiResponse.failure(ErrorCode.INDEX_REBUILD_ALREADY_RUNNING))
            }

            is UnavailableIndexRebuildExecution -> {
                log.warn("Manual index rebuild skipped because the execution lock could not be checked", execution.cause)

                ResponseEntity.status(ErrorCode.INDEX_REBUILD_LOCK_UNAVAILABLE.status)
                    .body(ApiResponse.failure(ErrorCode.INDEX_REBUILD_LOCK_UNAVAILABLE))
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(InternalStoryIndexController::class.java)
    }
}
