package com.beomsoo.shop.payment.api

import java.util.UUID

/** payment 모듈이 다른 모듈에 공개하는 동기 API */
interface PaymentFacade {

    /**
     * 결제한다. PG를 호출하므로 반드시 DB 트랜잭션 밖에서 호출해야 한다 (ADR-0006).
     * 트랜잭션 안에서 부르면 IllegalStateException이 난다.
     */
    fun pay(orderId: UUID, amount: Long): PaymentResult
}

sealed interface PaymentResult {

    val paymentId: UUID

    data class Approved(override val paymentId: UUID) : PaymentResult

    data class Failed(override val paymentId: UUID, val reason: String) : PaymentResult
}
