package com.beomsoo.shop.payment.infrastructure.persistence

import com.beomsoo.shop.payment.domain.Payment
import com.beomsoo.shop.payment.domain.PaymentId
import com.beomsoo.shop.payment.domain.PaymentStatus
import com.beomsoo.shop.shared.domain.Money
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payment")
class PaymentJpaEntity(
    @Id
    val id: UUID,
    val orderId: UUID,
    val amount: Long,
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus,
    var pgTransactionId: String?,
    var failureReason: String?,
    val requestedAt: Instant,
    var completedAt: Instant?,
) {
    @Version
    var version: Long? = null

    fun update(payment: Payment) {
        status = payment.status
        pgTransactionId = payment.pgTransactionId
        failureReason = payment.failureReason
        completedAt = payment.completedAt
    }

    fun toDomain(): Payment = Payment(
        id = PaymentId(id),
        orderId = orderId,
        amount = Money(amount),
        status = status,
        pgTransactionId = pgTransactionId,
        failureReason = failureReason,
        requestedAt = requestedAt,
        completedAt = completedAt,
    )

    companion object {
        fun from(payment: Payment) = PaymentJpaEntity(
            id = payment.id.value,
            orderId = payment.orderId,
            amount = payment.amount.amount,
            status = payment.status,
            pgTransactionId = payment.pgTransactionId,
            failureReason = payment.failureReason,
            requestedAt = payment.requestedAt,
            completedAt = payment.completedAt,
        )
    }
}
