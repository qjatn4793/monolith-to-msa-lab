package com.beomsoo.shop.catalog.application.port.`in`

import com.beomsoo.shop.catalog.domain.ProductId

interface ChangeProductUseCase {

    fun changePrice(id: ProductId, newPrice: Long)

    fun stopSelling(id: ProductId)

    fun resumeSelling(id: ProductId)
}
