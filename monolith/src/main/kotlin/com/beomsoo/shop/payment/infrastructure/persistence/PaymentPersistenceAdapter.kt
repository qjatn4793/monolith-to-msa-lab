package com.beomsoo.shop.payment.infrastructure.persistence

import com.beomsoo.shop.payment.domain.Payment
import com.beomsoo.shop.payment.domain.PaymentId
import com.beomsoo.shop.payment.domain.PaymentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PaymentPersistenceAdapter(
    private val jpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(payment: Payment) {
        val entity = jpaRepository.findByIdOrNull(payment.id.value)
        if (entity == null) {
            jpaRepository.save(PaymentJpaEntity.from(payment))
        } else {
            entity.update(payment)
        }
    }

    override fun findById(id: PaymentId): Payment? = jpaRepository.findByIdOrNull(id.value)?.toDomain()

    override fun findByOrderId(orderId: UUID): List<Payment> =
        jpaRepository.findByOrderIdOrderByIdAsc(orderId).map { it.toDomain() }
}
