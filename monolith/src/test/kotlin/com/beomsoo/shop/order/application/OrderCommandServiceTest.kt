package com.beomsoo.shop.order.application

import com.beomsoo.shop.order.application.port.`in`.PlaceOrderCommand
import com.beomsoo.shop.order.application.port.out.LoadOrdererPort
import com.beomsoo.shop.order.application.port.out.LoadProductPort
import com.beomsoo.shop.order.application.port.out.OrderableProduct
import com.beomsoo.shop.order.application.port.out.Orderer
import com.beomsoo.shop.order.application.port.out.StockPort
import com.beomsoo.shop.order.application.port.out.StockReservation
import com.beomsoo.shop.order.application.service.OrderCommandService
import com.beomsoo.shop.order.domain.InactiveOrdererException
import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.order.domain.OrderLine
import com.beomsoo.shop.order.domain.OrderRepository
import com.beomsoo.shop.order.domain.OrderStatus
import com.beomsoo.shop.order.domain.OrdererNotFoundException
import com.beomsoo.shop.order.domain.OutOfStockException
import com.beomsoo.shop.order.domain.ProductNotOrderableException
import com.beomsoo.shop.shared.domain.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 주문 서비스는 포트에만 의존하므로, 포트를 가짜 구현으로 바꿔 끼우면 Spring도 DB도 다른 모듈도 없이 테스트할 수 있다.
 * member, catalog, inventory 모듈이 없어도 이 테스트는 돈다. 이것이 헥사고날 구조가 주는 테스트 용이성이다.
 */
class OrderCommandServiceTest {

    private val memberId = UUID.randomUUID()
    private val keyboard = OrderableProduct(UUID.randomUUID(), "키보드", Money(50_000), onSale = true)
    private val mouse = OrderableProduct(UUID.randomUUID(), "마우스", Money(20_000), onSale = true)

    private val orders = FakeOrderRepository()
    private val orderers = FakeOrdererPort(Orderer(memberId, active = true))
    private val products = FakeProductPort(keyboard, mouse)
    private val stock = FakeStockPort()
    private val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)

    private val service = OrderCommandService(orders, orderers, products, stock, clock)

    @Test
    fun `주문 시점의 상품명과 가격을 스냅샷으로 저장하고 재고를 예약한다`() {
        val id = service.place(command(keyboard.productId to 2, mouse.productId to 1))

        val order = orders.getValue(id)
        assertEquals(Money(120_000), order.totalAmount)
        assertEquals(listOf("키보드", "마우스"), order.lines.map { it.productName })
        assertEquals(order.lines, stock.reserved)
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
    fun `재고가 부족하면 주문을 저장하지 않는다`() {
        stock.outOfStock = listOf(keyboard.productId)

        assertThrows<OutOfStockException> { service.place(command(keyboard.productId to 1)) }
        assertTrue(orders.isEmpty())
    }

    @Test
    fun `주문을 취소하면 예약한 재고를 해제한다`() {
        val id = service.place(command(keyboard.productId to 2))

        service.cancel(id)

        assertEquals(OrderStatus.CANCELLED, orders.getValue(id).status)
        assertEquals(orders.getValue(id).lines, stock.released)
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
        val released = mutableListOf<OrderLine>()

        override fun reserve(lines: List<OrderLine>): StockReservation {
            val shortages = lines.map { it.productId }.filter { it in outOfStock }
            if (shortages.isNotEmpty()) return StockReservation.OutOfStock(shortages)
            reserved += lines
            return StockReservation.Reserved
        }

        override fun release(lines: List<OrderLine>) { released += lines }
    }
}
