package com.beomsoo.shop.order.api

import java.time.Instant
import java.util.UUID

/*
 * 통합 이벤트: 다른 모듈에 공개하는 계약이다 (ADR-0005).
 * 도메인 타입 대신 UUID, Long, String 같은 기본 타입만 쓴다.
 * 이벤트 발행 저장소에 JSON으로 저장되고, MSA로 전환하면 그대로 Kafka 메시지 스키마가 된다.
 * 그래서 필드를 빼거나 이름을 바꾸면 안 된다. 추가만 한다.
 */

data class OrderConfirmedEvent(
    val orderId: UUID,
    val memberId: UUID,
    val totalAmount: Long,
    val occurredAt: Instant,
)

data class OrderCancelledEvent(
    val orderId: UUID,
    val memberId: UUID,
    /** PAYMENT_FAILED, REQUESTED */
    val reason: String,
    val occurredAt: Instant,
)
