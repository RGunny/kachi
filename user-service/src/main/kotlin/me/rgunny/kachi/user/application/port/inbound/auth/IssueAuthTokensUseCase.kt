package me.rgunny.kachi.user.application.port.inbound.auth

import me.rgunny.kachi.user.application.port.inbound.auth.model.IssueAuthTokensCommand
import me.rgunny.kachi.user.application.port.inbound.auth.model.IssueAuthTokensResult

/** 인증 성공 사용자에게 access/refresh token을 발급하는 유스케이스를 입력 어댑터에 제공하는 포트 */
interface IssueAuthTokensUseCase {

    fun issue(command: IssueAuthTokensCommand): IssueAuthTokensResult
}
