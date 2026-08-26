package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.ActiveKeywordResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.application.port.inbound.keyword.ListActiveKeywordsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 활성 키워드 internal API.
 *
 * 인증 없이 열려 있으며 수집·요약 서비스가 기동 주기마다 호출한다. 사용자 정보는 내보내지 않는다.
 */
@RestController
class InternalKeywordController(
    private val listActiveKeywordsUseCase: ListActiveKeywordsUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_ACTIVE_KEYWORDS, version = ApiVersions.V1)
    fun listActiveKeywords(): ResponseEntity<ApiResponse<List<ActiveKeywordResponse>>> {
        // 1. enabled 구독이 하나 이상인 canonical 키워드를 조회한다.
        val response = listActiveKeywordsUseCase.listActiveKeywords()
            .map(ActiveKeywordResponse::from)

        // 2. 소비자가 name만 읽어도 되도록 canonicalKey를 name에 함께 싣는다.
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
