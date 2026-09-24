package com.beomsoo.shop.order.application.port.out

import java.util.UUID

/**
 * 아웃바운드 포트: 주문자 정보를 가져온다.
 *
 * order 모듈의 언어로 정의한 인터페이스다. 주문에 필요한 건 "주문할 수 있는 회원인가"뿐이라
 * 회원의 모든 정보가 아니라 그만큼만 가져온다. 구현은 infrastructure/adapter/MemberAdapter가 한다.
 */
interface LoadOrdererPort {

    fun loadOrderer(memberId: UUID): Orderer?
}

data class Orderer(
    val memberId: UUID,
    val active: Boolean,
)
