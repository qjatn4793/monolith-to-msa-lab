package com.beomsoo.shop.payment.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import com.beomsoo.shop.shared.domain.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class PaymentTest {

    private val now = Instant.parse("2026-09-24T00:00:00Z")

    @Test
    fun `요청한 결제만 승인하거나 실패 처리할 수 있다`() {
        val approved = Payment.request(UUID.randomUUID(), Money(10_000), now).apply { approve("pg-1", now) }

        assertEquals(PaymentStatus.APPROVED, approved.status)
        assertEquals("pg-1", approved.pgTransactionId)
        assertThrows<PaymentAlreadyCompletedException> { approved.fail("late", now) }
        assertThrows<PaymentAlreadyCompletedException> { approved.approve("pg-2", now) }
    }

    @Test
    fun `0원은 결제할 수 없다`() {
        assertThrows<InvalidInputException> { Payment.request(UUID.randomUUID(), Money.ZERO, now) }
    }
}
