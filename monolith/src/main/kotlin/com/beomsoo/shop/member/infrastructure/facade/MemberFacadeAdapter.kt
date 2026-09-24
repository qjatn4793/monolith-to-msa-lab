package com.beomsoo.shop.member.infrastructure.facade

import com.beomsoo.shop.member.api.MemberFacade
import com.beomsoo.shop.member.api.MemberInfo
import com.beomsoo.shop.member.application.port.`in`.GetMemberQuery
import com.beomsoo.shop.member.domain.MemberId
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 인바운드 어댑터. 다른 모듈도 웹 클라이언트처럼 이 모듈을 "사용하는 쪽"이다.
 * 그래서 컨트롤러와 같은 위치(infrastructure)에서 인바운드 포트(유스케이스)를 호출한다.
 */
@Component
class MemberFacadeAdapter(
    private val getMemberQuery: GetMemberQuery,
) : MemberFacade {

    override fun findMember(memberId: UUID): MemberInfo? =
        getMemberQuery.findMember(MemberId(memberId))?.let {
            MemberInfo(id = it.id.value, email = it.email.value, name = it.name, active = it.isActive)
        }
}
