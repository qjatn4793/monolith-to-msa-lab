package com.beomsoo.shop.order.application.port.out

import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.shared.domain.Money

/** 아웃바운드 포트: 결제. 구현은 infrastructure/adapter/PaymentAdapter가 payment 모듈의 api를 호출한다. */
interface RequestPaymentPort {

    /** 외부 PG 호출이 포함되므로 DB 트랜잭션 밖에서 호출해야 한다 (ADR-0006). */
    fun pay(orderId: OrderId, amount: Money): PaymentResult
}

sealed interface PaymentResult {

    data object Paid : PaymentResult

    data class Failed(val reason: String) : PaymentResult
}
