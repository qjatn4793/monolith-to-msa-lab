package com.beomsoo.shop.order.application

import com.beomsoo.shop.order.application.port.`in`.PlaceOrderCommand
import com.beomsoo.shop.order.application.port.out.LoadOrdererPort
import com.beomsoo.shop.order.application.port.out.LoadProductPort
import com.beomsoo.shop.order.application.port.out.OrderEventPublisher
import com.beomsoo.shop.order.application.port.out.OrderableProduct
import com.beomsoo.shop.order.application.port.out.Orderer
import com.beomsoo.shop.order.application.port.out.PaymentResult
import com.beomsoo.shop.order.application.port.out.RequestPaymentPort
import com.beomsoo.shop.order.application.port.out.StockPort
import com.beomsoo.shop.order.application.port.out.StockReservation
import com.beomsoo.shop.order.application.service.OrderCommandService
import com.beomsoo.shop.order.domain.CancelReason
import com.beomsoo.shop.order.domain.InactiveOrdererException
import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.order.domain.OrderLine
import com.beomsoo.shop.order.domain.OrderRepository
import com.beomsoo.shop.order.domain.OrderStatus
import com.beomsoo.shop.order.domain.OrdererNotFoundException
import com.beomsoo.shop.order.domain.OutOfStockException
import com.beomsoo.shop.order.domain.ProductNotOrderableException
import com.beomsoo.shop.order.domain.event.OrderCancelled
import com.beomsoo.shop.order.domain.event.OrderConfirmed
import com.beomsoo.shop.order.domain.event.OrderEvent
import com.beomsoo.shop.shared.domain.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 주문 서비스는 포트에만 의존하므로, 포트를 가짜 구현으로 바꿔 끼우면 Spring도 DB도 다른 모듈도 없이 테스트할 수 있다.
 * 트랜잭션도 TransactionOperations.withoutTransaction()으로 대신한다.
 */
class OrderCommandServiceTest {

    private val memberId = UUID.randomUUID()
    private val keyboard = OrderableProduct(UUID.randomUUID(), "키보드", Money(50_000), onSale = true)
    private val mouse = OrderableProduct(UUID.randomUUID(), "마우스", Money(20_000), onSale = true)

    private val orders = FakeOrderRepository()
    private val orderers = FakeOrdererPort(Orderer(memberId, active = true))
    private val products = FakeProductPort(keyboard, mouse)
    private val stock = FakeStockPort()
    private val payment = FakePaymentPort()
    private val events = FakeEventPublisher()
    private val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)

    private val service = OrderCommandService(
        orders, orderers, products, stock, payment, events, TransactionOperations.withoutTransaction(), clock,
    )

    @Test
    fun `결제가 승인되면 주문을 확정하고 재고 차감을 확정하고 확정 이벤트를 발행한다`() {
        val result = service.place(command(keyboard.productId to 2, mouse.productId to 1))

        val order = orders.getValue(result.orderId)
        assertEquals(OrderStatus.CONFIRMED, result.status)
        assertEquals(Money(120_000), order.totalAmount)
        assertEquals(listOf("키보드", "마우스"), order.lines.map { it.productName })
        assertEquals(order.lines, stock.reserved)
        assertEquals(order.lines, stock.confirmed)
        assertEquals(listOf(Money(120_000)), payment.requestedAmounts)
        assertIs<OrderConfirmed>(events.published.single())
    }

    @Test
    fun `결제가 실패하면 주문을 취소하고 재고 예약을 해제하고 취소 이벤트를 발행한다`() {
        payment.nextResult = PaymentResult.Failed("LIMIT_EXCEEDED")

        val result = service.place(command(keyboard.productId to 1))

        val order = orders.getValue(result.orderId)
        assertEquals(OrderStatus.CANCELLED, result.status)
        assertEquals(CancelReason.PAYMENT_FAILED, order.cancelReason)
        assertEquals(order.lines, stock.released)
        assertTrue(stock.confirmed.isEmpty())
        assertEquals(CancelReason.PAYMENT_FAILED, assertIs<OrderCancelled>(events.published.single()).reason)
    }

    @Test
    fun `재고가 부족하면 결제를 요청하지 않고 주문도 저장하지 않는다`() {
        stock.outOfStock = listOf(keyboard.productId)

        assertThrows<OutOfStockException> { service.place(command(keyboard.productId to 1)) }
        assertTrue(orders.isEmpty())
        assertTrue(payment.requestedAmounts.isEmpty())
        assertTrue(events.published.isEmpty())
    }

    @Test
    fun `없는 회원이나 탈퇴한 회원은 주문할 수 없다`() {
        assertThrows<OrdererNotFoundException> { service.place(command(keyboard.productId to 1, memberId = UUID.randomUUID())) }

        orderers.orderer = Orderer(memberId, active = false)
        assertThrows<InactiveOrdererException> { service.place(command(keyboard.productId to 1)) }
    }

    @Test
    fun `없는 상품이나 판매 중지된 상품은 주문할 수 없다`() {
        assertThrows<ProductNotOrderableException> { service.place(command(UUID.randomUUID() to 1)) }

        products.stopSelling(keyboard.productId)
        assertThrows<ProductNotOrderableException> { service.place(command(keyboard.productId to 1)) }
    }

    @Test
    fun `결제 대기 중인 주문을 취소하면 재고 예약을 해제한다`() {
        val order = Order.place(memberId, listOf(OrderLine(keyboard.productId, "키보드", Money(50_000), 1)), clock.instant())
        orders.save(order)

        service.cancel(order.id)

        assertEquals(OrderStatus.CANCELLED, orders.getValue(order.id).status)
        assertEquals(CancelReason.REQUESTED, orders.getValue(order.id).cancelReason)
        assertEquals(order.lines, stock.released)
    }

    private fun command(vararg items: Pair<UUID, Int>, memberId: UUID = this.memberId) =
        PlaceOrderCommand(memberId, items.map { (productId, quantity) -> PlaceOrderCommand.Item(productId, quantity) })

    // ── 가짜 구현들. 포트 인터페이스가 작아서 가짜도 짧다. ─────────────────────────

    private class FakeOrderRepository : OrderRepository {
        private val store = mutableMapOf<OrderId, Order>()
        override fun save(order: Order) { store[order.id] = order }
        override fun findById(id: OrderId): Order? = store[id]
        fun getValue(id: OrderId): Order = store.getValue(id)
        fun isEmpty(): Boolean = store.isEmpty()
    }

    private class FakeOrdererPort(var orderer: Orderer) : LoadOrdererPort {
        override fun loadOrderer(memberId: UUID): Orderer? = orderer.takeIf { it.memberId == memberId }
    }

    private class FakeProductPort(vararg products: OrderableProduct) : LoadProductPort {
        private val store = products.associateBy { it.productId }.toMutableMap()
        override fun loadProducts(productIds: Collection<UUID>) = productIds.mapNotNull { store[it] }
        fun stopSelling(productId: UUID) { store[productId] = store.getValue(productId).copy(onSale = false) }
    }

    private class FakeStockPort : StockPort {
        var outOfStock: List<UUID> = emptyList()
        val reserved = mutableListOf<OrderLine>()
        val confirmed = mutableListOf<OrderLine>()
        val released = mutableListOf<OrderLine>()

        override fun reserve(lines: List<OrderLine>): StockReservation {
            val shortages = lines.map { it.productId }.filter { it in outOfStock }
            if (shortages.isNotEmpty()) return StockReservation.OutOfStock(shortages)
            reserved += lines
            return StockReservation.Reserved
        }

        override fun confirm(lines: List<OrderLine>) { confirmed += lines }
        override fun release(lines: List<OrderLine>) { released += lines }
    }

    private class FakePaymentPort : RequestPaymentPort {
        var nextResult: PaymentResult = PaymentResult.Paid
        val requestedAmounts = mutableListOf<Money>()

        override fun pay(orderId: OrderId, amount: Money): PaymentResult {
            requestedAmounts += amount
            return nextResult
        }
    }

    private class FakeEventPublisher : OrderEventPublisher {
        val published = mutableListOf<OrderEvent>()
        override fun publish(event: OrderEvent) { published += event }
    }
}
