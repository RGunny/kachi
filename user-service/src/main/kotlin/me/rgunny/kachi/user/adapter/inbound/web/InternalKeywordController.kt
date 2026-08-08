package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.ActiveKeywordResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.inbound.keyword.ListActiveKeywordsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class InternalKeywordController(
    private val listActiveKeywordsUseCase: ListActiveKeywordsUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_ACTIVE_KEYWORDS, version = ApiVersions.V1)
    fun listActiveKeywords(): ResponseEntity<ApiResponse<List<ActiveKeywordResponse>>> {
        // 1. user-service가 소유한 keyword 저장소에서 활성 키워드를 조회한다.
        val response = listActiveKeywordsUseCase.listActiveKeywords()
            .map(ActiveKeywordResponse::from)

        // 2. collector-service가 그대로 읽을 수 있도록 keyword 이름 목록만 리턴한다.
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
