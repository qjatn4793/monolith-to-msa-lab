package com.beomsoo.shop.inventory.application.port.`in`

import com.beomsoo.shop.inventory.domain.Stock
import java.util.UUID

interface GetStockQuery {

    fun getStock(productId: UUID): Stock
}
