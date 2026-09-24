package com.beomsoo.shop.order.application.port.`in`

import com.beomsoo.shop.order.domain.OrderId

interface CancelOrderUseCase {

    fun cancel(id: OrderId)
}
