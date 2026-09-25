package com.beomsoo.shop.payment.infrastructure.web

import com.beomsoo.shop.payment.application.port.`in`.GetPaymentQuery
import com.beomsoo.shop.payment.domain.Payment
import com.beomsoo.shop.payment.domain.PaymentId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val getPaymentQuery: GetPaymentQuery,
) {

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): PaymentResponse = PaymentResponse.from(getPaymentQuery.getPayment(PaymentId(id)))

    @GetMapping
    fun listOfOrder(@RequestParam orderId: UUID): List<PaymentResponse> =
        getPaymentQuery.getPaymentsOfOrder(orderId).map(PaymentResponse::from)
}

data class PaymentResponse(
    val id: UUID,
    val orderId: UUID,
    val amount: Long,
    val status: String,
    val pgTransactionId: String?,
    val failureReason: String?,
    val requestedAt: Instant,
    val completedAt: Instant?,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            id = payment.id.value,
            orderId = payment.orderId,
            amount = payment.amount.amount,
            status = payment.status.name,
            pgTransactionId = payment.pgTransactionId,
            failureReason = payment.failureReason,
            requestedAt = payment.requestedAt,
            completedAt = payment.completedAt,
        )
    }
}
