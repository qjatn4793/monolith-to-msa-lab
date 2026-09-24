package com.beomsoo.shop.order.infrastructure.adapter

import com.beomsoo.shop.member.api.MemberFacade
import com.beomsoo.shop.order.application.port.out.LoadOrdererPort
import com.beomsoo.shop.order.application.port.out.Orderer
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 아웃바운드 어댑터: order → member.
 *
 * order 모듈에서 member 모듈을 아는 곳은 이 클래스 하나뿐이고, 그마저도 member.api만 안다 (ADR-0005).
 * member를 별도 서비스로 떼어내면 이 클래스만 HTTP 클라이언트 호출로 바꾸면 된다.
 */
@Component
class MemberAdapter(
    private val memberFacade: MemberFacade,
) : LoadOrdererPort {

    override fun loadOrderer(memberId: UUID): Orderer? =
        memberFacade.findMember(memberId)?.let { Orderer(memberId = it.id, active = it.active) }
}
