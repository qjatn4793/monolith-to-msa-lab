package com.beomsoo.shop.order.application.port.out

import com.beomsoo.shop.order.domain.OrderLine
import java.util.UUID

interface StockPort {

    fun reserve(lines: List<OrderLine>): StockReservation

    fun release(lines: List<OrderLine>)
}

sealed interface StockReservation {

    data object Reserved : StockReservation

    data class OutOfStock(val productIds: List<UUID>) : StockReservation
}
