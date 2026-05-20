package me.rgunny.kachi.user.application.port.`in`

/** 사용자 단건 조회 유스케이스를 외부 입력 어댑터에 제공하는 포트 */
interface GetUserUseCase {

    fun get(query: GetUserQuery): GetUserResult
}
