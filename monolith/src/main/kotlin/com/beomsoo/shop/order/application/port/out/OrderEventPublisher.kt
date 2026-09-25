package com.beomsoo.shop.order.application.port.out

import com.beomsoo.shop.order.domain.event.OrderEvent

/**
 * 아웃바운드 포트: 도메인 이벤트 발행.
 *
 * 반드시 주문 상태를 바꾸는 트랜잭션 **안에서** 호출한다. 그래야 이벤트 발행 저장소(Event Publication Registry)가
 * 주문 변경과 같은 트랜잭션에 "이 이벤트를 누구에게 전달해야 한다"는 기록을 남긴다 (ADR-0011).
 */
interface OrderEventPublisher {

    fun publish(event: OrderEvent)
}
