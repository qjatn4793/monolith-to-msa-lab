package com.beomsoo.shop.payment.domain

import java.util.UUID

interface PaymentRepository {

    fun save(payment: Payment)

    fun findById(id: PaymentId): Payment?

    fun findByOrderId(orderId: UUID): List<Payment>
}
