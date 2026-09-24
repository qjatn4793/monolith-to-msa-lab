package com.beomsoo.shop.order.application.port.`in`

import com.beomsoo.shop.order.domain.OrderId
import java.util.UUID

interface PlaceOrderUseCase {

    fun place(command: PlaceOrderCommand): OrderId
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
