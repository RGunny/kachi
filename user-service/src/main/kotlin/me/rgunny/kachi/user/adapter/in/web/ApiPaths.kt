package me.rgunny.kachi.user.adapter.`in`.web

/**
 * user-service HTTP API Path Contract.
 *
 * Spring Framework 7 API Versioning을 기준으로 버전별 경로를 구성한다.
 * Controller mapping과 Security matcher가 같은 경로를 참조하도록 관리한다.
 */
object ApiPaths {
    const val AUTH_TOKEN_REFRESH = "/auth/token/refresh"
    const val AUTH_LOGOUT = "/auth/logout"
    const val USERS = "/users"
    const val ME = "/me"
    const val ME_KEYWORDS = "/me/keywords"
    const val KEYWORDS = "/keywords/{keywordId}"

    const val V1_AUTH_TOKEN_REFRESH = "${ApiVersions.V1_PATH_PREFIX}$AUTH_TOKEN_REFRESH"
    const val V1_AUTH_LOGOUT = "${ApiVersions.V1_PATH_PREFIX}$AUTH_LOGOUT"
    const val V1_USERS = "${ApiVersions.V1_PATH_PREFIX}$USERS"
    const val V1_ME = "${ApiVersions.V1_PATH_PREFIX}$ME"
    const val V1_ME_KEYWORDS = "${ApiVersions.V1_PATH_PREFIX}$ME_KEYWORDS"
    const val V1_KEYWORDS = "${ApiVersions.V1_PATH_PREFIX}$KEYWORDS"
}
