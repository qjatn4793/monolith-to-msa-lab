package com.beomsoo.shop.payment.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, UUID> {

    fun findByOrderIdOrderByIdAsc(orderId: UUID): List<PaymentJpaEntity>
}
