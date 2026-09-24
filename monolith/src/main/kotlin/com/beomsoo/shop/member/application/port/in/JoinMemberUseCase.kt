package com.beomsoo.shop.member.application.port.`in`

import com.beomsoo.shop.member.domain.MemberId

/**
 * 인바운드 포트: 이 모듈이 바깥(웹, 다른 모듈)에 제공하는 기능.
 * 컨트롤러는 서비스 구현이 아니라 이 인터페이스에 의존한다.
 */
interface JoinMemberUseCase {

    fun join(command: JoinMemberCommand): MemberId
}

data class JoinMemberCommand(
    val email: String,
    val name: String,
)
