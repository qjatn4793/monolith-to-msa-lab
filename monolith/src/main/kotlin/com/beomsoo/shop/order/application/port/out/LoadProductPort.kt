package com.beomsoo.shop.order.application.port.out

import com.beomsoo.shop.shared.domain.Money
import java.util.UUID

interface LoadProductPort {

    /** 존재하는 상품만 돌려준다 */
    fun loadProducts(productIds: Collection<UUID>): List<OrderableProduct>
}

data class OrderableProduct(
    val productId: UUID,
    val name: String,
    val price: Money,
    val onSale: Boolean,
)
