package com.beomsoo.shop.order.infrastructure.adapter

import com.beomsoo.shop.order.api.OrderCancelledEvent
import com.beomsoo.shop.order.api.OrderConfirmedEvent
import com.beomsoo.shop.order.application.port.out.OrderEventPublisher
import com.beomsoo.shop.order.domain.event.OrderCancelled
import com.beomsoo.shop.order.domain.event.OrderConfirmed
import com.beomsoo.shop.order.domain.event.OrderEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 아웃바운드 어댑터: 도메인 이벤트를 통합 이벤트(order.api)로 바꿔서 Spring 이벤트로 발행한다.
 *
 * 구독자가 @ApplicationModuleListener면 Spring Modulith가 이 발행을 이벤트 발행 저장소(EVENT_PUBLICATION 테이블)에
 * 현재 트랜잭션과 함께 기록한다. 트랜잭션이 롤백되면 기록도 사라지고, 커밋되면 구독자가 실패해도 기록이 남는다 (ADR-0011).
 */
@Component
class OrderEventPublisherAdapter(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : OrderEventPublisher {

    override fun publish(event: OrderEvent) {
        applicationEventPublisher.publishEvent(event.toIntegrationEvent())
    }

    private fun OrderEvent.toIntegrationEvent(): Any = when (this) {
        is OrderConfirmed -> OrderConfirmedEvent(orderId.value, memberId, totalAmount.amount, occurredAt)
        is OrderCancelled -> OrderCancelledEvent(orderId.value, memberId, reason.name, occurredAt)
    }
}
