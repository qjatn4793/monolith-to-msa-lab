package com.beomsoo.shop.order.application.port.`in`

import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.order.domain.OrderStatus
import java.util.UUID

interface PlaceOrderUseCase {

    /** 주문하고 결제까지 마친다. 결제가 실패해도 예외가 아니라 CANCELLED 상태의 결과를 돌려준다. */
    fun place(command: PlaceOrderCommand): PlaceOrderResult
}

data class PlaceOrderCommand(
    val memberId: UUID,
    val items: List<Item>,
) {
    data class Item(
        val productId: UUID,
        val quantity: Int,
    )
}

data class PlaceOrderResult(
    val orderId: OrderId,
    val status: OrderStatus,
)
