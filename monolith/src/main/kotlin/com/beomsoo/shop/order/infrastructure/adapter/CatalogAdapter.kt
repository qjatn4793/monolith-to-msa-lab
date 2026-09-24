package com.beomsoo.shop.order.infrastructure.adapter

import com.beomsoo.shop.catalog.api.CatalogFacade
import com.beomsoo.shop.order.application.port.out.LoadProductPort
import com.beomsoo.shop.order.application.port.out.OrderableProduct
import com.beomsoo.shop.shared.domain.Money
import org.springframework.stereotype.Component
import java.util.UUID

/** 아웃바운드 어댑터: order → catalog */
@Component
class CatalogAdapter(
    private val catalogFacade: CatalogFacade,
) : LoadProductPort {

    override fun loadProducts(productIds: Collection<UUID>): List<OrderableProduct> =
        catalogFacade.findProducts(productIds).map {
            OrderableProduct(productId = it.id, name = it.name, price = Money(it.price), onSale = it.onSale)
        }
}
