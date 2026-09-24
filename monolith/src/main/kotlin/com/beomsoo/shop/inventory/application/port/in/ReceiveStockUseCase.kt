package com.beomsoo.shop.inventory.application.port.`in`

import java.util.UUID

interface ReceiveStockUseCase {

    /** 입고. 재고 정보가 없으면 새로 만든다. */
    fun receive(productId: UUID, quantity: Int)
}
