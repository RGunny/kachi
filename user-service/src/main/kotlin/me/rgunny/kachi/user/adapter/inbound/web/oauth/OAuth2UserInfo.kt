package me.rgunny.kachi.user.adapter.inbound.web.oauth

/**
 * provider마다 다른 OAuth2 사용자 응답을 application 계층에 넘길 공통 형태로 읽는 어댑터 모델.
 */
interface OAuth2UserInfo {
    val providerId: String
    val email: String
    val nickname: String
}
