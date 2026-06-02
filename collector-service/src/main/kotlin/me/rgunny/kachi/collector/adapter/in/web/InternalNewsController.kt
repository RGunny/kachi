package me.rgunny.kachi.collector.adapter.`in`.web

import me.rgunny.kachi.collector.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.collector.adapter.`in`.web.response.ErrorCode
import me.rgunny.kachi.collector.application.port.`in`.ListNewsQuery
import me.rgunny.kachi.collector.application.port.`in`.ListNewsUseCase
import me.rgunny.kachi.collector.domain.CollectedKeyword
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class InternalNewsController(
    private val listNewsUseCase: ListNewsUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_NEWS, version = ApiVersions.V1)
    suspend fun listNews(
        @RequestParam keyword: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: Instant?,
        @RequestParam(required = false, defaultValue = "${ListNewsQuery.DEFAULT_LIMIT}")
        limit: Int
    ): ResponseEntity<ApiResponse<*>> {
        val query = runCatching {
            ListNewsQuery(
                keyword = CollectedKeyword.of(keyword),
                from = from,
                to = to,
                limit = limit
            )
        }.getOrElse { error ->
            return ResponseEntity.status(ErrorCode.INVALID_NEWS_QUERY.status)
                .body(ApiResponse.failure(ErrorCode.INVALID_NEWS_QUERY, error.message))
        }

        return ResponseEntity.ok(ApiResponse.success(listNewsUseCase.listNews(query)))
    }
}
