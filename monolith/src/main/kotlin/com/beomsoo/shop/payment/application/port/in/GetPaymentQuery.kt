package com.beomsoo.shop.payment.application.port.`in`

import com.beomsoo.shop.payment.domain.Payment
import com.beomsoo.shop.payment.domain.PaymentId
import java.util.UUID

interface GetPaymentQuery {

    fun getPayment(id: PaymentId): Payment

    fun getPaymentsOfOrder(orderId: UUID): List<Payment>
}
