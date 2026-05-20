package me.rgunny.kachi.user.adapter.`in`.web.response

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

        fun failure(error: ErrorResponse): ApiResponse<Unit> {
            return ApiResponse(
                success = false,
                data = null,
                error = error
            )
        }
    }
}
