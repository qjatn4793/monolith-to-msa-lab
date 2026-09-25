package com.beomsoo.shop.inventory.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StockTest {

    private fun stock(available: Int) = Stock(UUID.randomUUID(), available, reserved = 0)

    @Test
    fun `예약하면 가용 재고가 예약 재고로 옮겨간다`() {
        val stock = stock(10)
        stock.reserve(3)

        assertEquals(7, stock.available)
        assertEquals(3, stock.reserved)
    }

    @Test
    fun `가용 재고보다 많이 예약할 수 없다`() {
        val stock = stock(2)

        assertFalse(stock.canReserve(3))
        assertThrows<InsufficientStockException> { stock.reserve(3) }
        assertEquals(2, stock.available)
    }

    @Test
    fun `해제하면 예약 재고가 가용 재고로 돌아간다`() {
        val stock = stock(10)
        stock.reserve(3)
        stock.release(3)

        assertEquals(10, stock.available)
        assertEquals(0, stock.reserved)
    }

    @Test
    fun `확정하면 예약 재고에서 빠지고 가용 재고로 돌아가지 않는다`() {
        val stock = stock(10)
        stock.reserve(3)
        stock.confirm(3)

        assertEquals(7, stock.available)
        assertEquals(0, stock.reserved)
        assertThrows<ReservedStockExceededException> { stock.confirm(1) }
    }

    @Test
    fun `예약한 것보다 많이 해제할 수 없다`() {
        val stock = stock(10)
        stock.reserve(3)

        assertThrows<ReservedStockExceededException> { stock.release(4) }
    }

    @Test
    fun `수량은 1 이상이어야 한다`() {
        assertThrows<InvalidInputException> { stock(10).receive(0) }
        assertThrows<InvalidInputException> { stock(10).reserve(-1) }
    }
}
