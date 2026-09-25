package com.beomsoo.shop.payment.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import com.beomsoo.shop.shared.domain.Money
import com.beomsoo.shop.shared.domain.newId
import java.time.Instant
import java.util.UUID

@JvmInline
value class PaymentId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): PaymentId = PaymentId(newId())
    }
}

/**
 * 결제 한 건. PG에 요청하기 전에 REQUESTED로 먼저 기록하고, PG 응답을 받은 뒤 APPROVED나 FAILED로 바꾼다.
 * 요청 기록이 먼저 남아 있어야, PG 호출 중에 프로세스가 죽어도 "결제를 시도했다"는 사실을 알 수 있다.
 */
class Payment(
    val id: PaymentId,
    val orderId: UUID,
    val amount: Money,
    status: PaymentStatus,
    pgTransactionId: String?,
    failureReason: String?,
    val requestedAt: Instant,
    completedAt: Instant?,
) {
    var status: PaymentStatus = status
        private set

    /** PG가 발급한 거래 번호. 승인됐을 때만 있다. */
    var pgTransactionId: String? = pgTransactionId
        private set

    var failureReason: String? = failureReason
        private set

    var completedAt: Instant? = completedAt
        private set

    init {
        if (amount == Money.ZERO) throw InvalidInputException("결제 금액은 0원일 수 없습니다.")
    }

    fun approve(pgTransactionId: String, now: Instant) {
        ensureRequested()
        status = PaymentStatus.APPROVED
        this.pgTransactionId = pgTransactionId
        completedAt = now
    }

    fun fail(reason: String, now: Instant) {
        ensureRequested()
        status = PaymentStatus.FAILED
        failureReason = reason
        completedAt = now
    }

    private fun ensureRequested() {
        if (status != PaymentStatus.REQUESTED) throw PaymentAlreadyCompletedException(id, status)
    }

    companion object {
        fun request(orderId: UUID, amount: Money, now: Instant): Payment =
            Payment(PaymentId.new(), orderId, amount, PaymentStatus.REQUESTED, null, null, now, null)
    }
}

enum class PaymentStatus {
    REQUESTED,
    APPROVED,
    FAILED,
}
