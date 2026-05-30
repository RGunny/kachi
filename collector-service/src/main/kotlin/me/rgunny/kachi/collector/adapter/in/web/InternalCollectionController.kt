package me.rgunny.kachi.collector.adapter.`in`.web

import me.rgunny.kachi.collector.adapter.`in`.collection.NewsCollectionExecutionResult
import me.rgunny.kachi.collector.adapter.`in`.collection.NewsCollectionExecutor
import me.rgunny.kachi.collector.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.collector.adapter.`in`.web.response.ErrorCode
import me.rgunny.kachi.collector.application.port.`in`.CollectNewsCommand
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * collector-service 내부 운영용 수집 실행 API를 제공한다.
 *
 * 외부 사용자 API가 아니라 서비스 운영자나 내부 시스템이 수동 수집을 트리거하는 진입점이다.
 */
@RestController
class InternalCollectionController(
    private val executor: NewsCollectionExecutor
) {

    @PostMapping(ApiPaths.INTERNAL_COLLECTIONS_NEWS, version = ApiVersions.V1)
    suspend fun collectNews(
        @RequestBody(required = false) request: CollectNewsRequest?
    ): ResponseEntity<ApiResponse<*>> {
        // 1. 요청 body를 수집 command로 변환하고 중복 실행 방지 컴포넌트에 위임한다.
        return when (val result = executor.execute(request.toCommand())) {
            is NewsCollectionExecutionResult.AlreadyRunning ->
                // 2. 이미 실행 중이면 클라이언트가 재시도 여부를 판단할 수 있도록 409를 반환한다.
                ResponseEntity.status(ErrorCode.COLLECTION_ALREADY_RUNNING.status)
                    .body(ApiResponse.failure(ErrorCode.COLLECTION_ALREADY_RUNNING))

            is NewsCollectionExecutionResult.Started ->
                // 3. 실행이 시작되어 완료된 결과를 내부 API 응답 DTO로 변환한다.
                ResponseEntity.ok(ApiResponse.success(CollectionRunResponse.from(result.result)))
        }
    }

    private fun CollectNewsRequest?.toCommand(): CollectNewsCommand {
        val requestOrDefault = this ?: CollectNewsRequest()

        // 요청 body가 없으면 전체 활성 키워드 수집으로 본다.
        // keywords가 비어 있으면 CollectNewsService가 user-service에서 활성 키워드를 조회한다.
        return CollectNewsCommand(
            keywords = requestOrDefault.keywords.map(CollectedKeyword::of),
            sources = requestOrDefault.sources.map { NewsSource.valueOf(it.trim().uppercase()) }.toSet()
        )
    }
}
