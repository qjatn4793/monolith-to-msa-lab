package com.beomsoo.shop.order.domain.event

import com.beomsoo.shop.order.domain.CancelReason
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.shared.domain.Money
import java.time.Instant
import java.util.UUID

/**
 * 도메인 이벤트: order 모듈 **내부**의 언어로 "무슨 일이 일어났는가"를 표현한다 (ADR-0005).
 * 도메인 타입(OrderId, Money)을 그대로 쓴다. 다른 모듈에 알릴 때는 인프라 어댑터가
 * 공개 계약인 통합 이벤트(order.api)로 바꿔서 발행한다.
 */
sealed interface OrderEvent {
    val orderId: OrderId
    val occurredAt: Instant
}

data class OrderConfirmed(
    override val orderId: OrderId,
    val memberId: UUID,
    val totalAmount: Money,
    override val occurredAt: Instant,
) : OrderEvent

data class OrderCancelled(
    override val orderId: OrderId,
    val memberId: UUID,
    val reason: CancelReason,
    override val occurredAt: Instant,
) : OrderEvent
