package me.rgunny.kachi.story.adapter.inbound.web.response

/**
 * 내부 API의 성공·실패 공통 응답 형식.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ErrorResponse?
) {
    companion object {

        fun <T> success(data: T): ApiResponse<T> {
            return ApiResponse(
                success = true,
                data = data,
                error = null
            )
        }

        fun failure(code: ErrorCode, message: String? = null): ApiResponse<Unit> {
            return ApiResponse(
                success = false,
                data = null,
                error = ErrorResponse.of(code, message)
            )
        }
    }
}
