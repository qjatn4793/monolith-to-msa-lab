package com.beomsoo.shop.order.application.port.out

import com.beomsoo.shop.order.domain.OrderLine
import java.util.UUID

interface StockPort {

    fun reserve(lines: List<OrderLine>): StockReservation

    /** 예약한 재고를 확정 차감한다 (결제 완료) */
    fun confirm(lines: List<OrderLine>)

    /** 예약한 재고를 해제한다 (주문 취소) */
    fun release(lines: List<OrderLine>)
}

sealed interface StockReservation {

    data object Reserved : StockReservation

    data class OutOfStock(val productIds: List<UUID>) : StockReservation
}
