package com.beomsoo.shop.payment.application.port.`in`

import com.beomsoo.shop.payment.domain.PaymentId
import java.util.UUID

interface RequestPaymentUseCase {

    /**
     * PG에 결제를 요청한다. 반드시 DB 트랜잭션 밖에서 호출해야 한다 (ADR-0006).
     * 결제 요청 기록과 결과 기록은 각각 짧은 트랜잭션으로 이 안에서 처리한다.
     */
    fun pay(command: RequestPaymentCommand): PaymentOutcome
}

data class RequestPaymentCommand(
    val orderId: UUID,
    val amount: Long,
)

sealed interface PaymentOutcome {

    val paymentId: PaymentId

    data class Approved(override val paymentId: PaymentId) : PaymentOutcome

    data class Failed(override val paymentId: PaymentId, val reason: String) : PaymentOutcome
}
