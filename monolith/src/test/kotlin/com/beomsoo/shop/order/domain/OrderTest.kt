package com.beomsoo.shop.order.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import com.beomsoo.shop.shared.domain.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class OrderTest {

    private val now = Instant.parse("2026-09-24T00:00:00Z")
    private val memberId = UUID.randomUUID()

    private fun line(price: Long, quantity: Int, productId: UUID = UUID.randomUUID()) =
        OrderLine(productId, "상품", Money(price), quantity)

    @Test
    fun `주문 금액은 상품별 금액의 합이다`() {
        val order = Order.place(memberId, listOf(line(1000, 2), line(500, 3)), now)

        assertEquals(Money(3500), order.totalAmount)
        assertEquals(OrderStatus.PENDING, order.status)
    }

    @Test
    fun `주문 상품이 없거나 같은 상품이 중복되면 주문할 수 없다`() {
        val productId = UUID.randomUUID()

        assertThrows<InvalidInputException> { Order.place(memberId, emptyList(), now) }
        assertThrows<InvalidInputException> {
            Order.place(memberId, listOf(line(1000, 1, productId), line(1000, 2, productId)), now)
        }
    }

    @Test
    fun `수량은 1개 이상 999개 이하다`() {
        assertThrows<InvalidInputException> { line(1000, 0) }
        assertThrows<InvalidInputException> { line(1000, 1000) }
    }

    @Test
    fun `결제 대기 중인 주문을 확정하면 확정 이벤트를 돌려준다`() {
        val order = Order.place(memberId, listOf(line(1000, 2)), now)

        val event = order.confirm(now)

        assertEquals(OrderStatus.CONFIRMED, order.status)
        assertEquals(order.id, event.orderId)
        assertEquals(Money(2000), event.totalAmount)
    }

    @Test
    fun `결제 대기 중인 주문만 확정하거나 취소할 수 있다`() {
        val confirmed = Order.place(memberId, listOf(line(1000, 1)), now).apply { confirm(now) }
        val cancelled = Order.place(memberId, listOf(line(1000, 1)), now).apply { cancel(CancelReason.REQUESTED, now) }

        assertThrows<OrderNotCancellableException> { confirmed.cancel(CancelReason.REQUESTED, now) }
        assertThrows<OrderNotConfirmableException> { cancelled.confirm(now) }
        assertEquals(CancelReason.REQUESTED, cancelled.cancelReason)
    }
}
