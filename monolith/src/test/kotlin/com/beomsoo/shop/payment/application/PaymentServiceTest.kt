package com.beomsoo.shop.payment.application

import com.beomsoo.shop.payment.application.port.`in`.PaymentOutcome
import com.beomsoo.shop.payment.application.port.`in`.RequestPaymentCommand
import com.beomsoo.shop.payment.application.port.out.PaymentGatewayPort
import com.beomsoo.shop.payment.application.port.out.PgApprovalRequest
import com.beomsoo.shop.payment.application.port.out.PgApprovalResult
import com.beomsoo.shop.payment.application.service.PaymentService
import com.beomsoo.shop.payment.domain.Payment
import com.beomsoo.shop.payment.domain.PaymentId
import com.beomsoo.shop.payment.domain.PaymentRepository
import com.beomsoo.shop.payment.domain.PaymentStatus
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PaymentServiceTest {

    private val payments = object : PaymentRepository {
        val store = mutableMapOf<PaymentId, Payment>()
        override fun save(payment: Payment) { store[payment.id] = payment }
        override fun findById(id: PaymentId) = store[id]
        override fun findByOrderId(orderId: UUID) = store.values.filter { it.orderId == orderId }
    }
    private var pgResult: PgApprovalResult = PgApprovalResult.Approved("pg-1")
    private val requests = mutableListOf<PgApprovalRequest>()
    private val gateway = object : PaymentGatewayPort {
        override fun approve(request: PgApprovalRequest): PgApprovalResult = pgResult.also { requests += request }
    }
    private val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)

    private val service = PaymentService(payments, gateway, TransactionOperations.withoutTransaction(), clock)

    @Test
    fun `PG가 승인하면 결제를 승인 상태로 기록한다`() {
        val outcome = service.pay(RequestPaymentCommand(UUID.randomUUID(), 10_000))

        val payment = payments.store.getValue(outcome.paymentId)
        assertIs<PaymentOutcome.Approved>(outcome)
        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals("pg-1", payment.pgTransactionId)
        // 결제 ID를 PG의 멱등성 키로 쓴다
        assertEquals(payment.id.toString(), requests.single().idempotencyKey)
    }

    @Test
    fun `PG가 거절하거나 응답하지 않으면 실패로 기록하고 이유를 남긴다`() {
        pgResult = PgApprovalResult.Declined("LIMIT_EXCEEDED")
        val declined = service.pay(RequestPaymentCommand(UUID.randomUUID(), 10_000))

        pgResult = PgApprovalResult.Unavailable("ResourceAccessException")
        val unavailable = service.pay(RequestPaymentCommand(UUID.randomUUID(), 10_000))

        assertEquals("DECLINED: LIMIT_EXCEEDED", payments.store.getValue(declined.paymentId).failureReason)
        assertEquals("PG_UNAVAILABLE: ResourceAccessException", payments.store.getValue(unavailable.paymentId).failureReason)
        assertEquals(PaymentOutcome.Failed(unavailable.paymentId, "PG_UNAVAILABLE"), unavailable)
    }
}
