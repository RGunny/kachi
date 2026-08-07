package me.rgunny.kachi.user.application.port.inbound.keyword

import me.rgunny.kachi.user.application.port.inbound.keyword.model.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.inbound.keyword.model.UpdateKeywordResult

/** 관심 키워드 수정 유스케이스를 외부 입력 어댑터에 제공하는 포트 */
interface UpdateKeywordUseCase {

    fun update(command: UpdateKeywordCommand): UpdateKeywordResult
}
