package com.beomsoo.shop.catalog.infrastructure.facade

import com.beomsoo.shop.catalog.api.CatalogFacade
import com.beomsoo.shop.catalog.api.ProductInfo
import com.beomsoo.shop.catalog.application.port.`in`.GetProductQuery
import com.beomsoo.shop.catalog.domain.ProductId
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CatalogFacadeAdapter(
    private val getProductQuery: GetProductQuery,
) : CatalogFacade {

    override fun findProducts(productIds: Collection<UUID>): List<ProductInfo> =
        getProductQuery.findProducts(productIds.map(::ProductId)).map {
            ProductInfo(id = it.id.value, name = it.name, price = it.price.amount, onSale = it.isOnSale)
        }
}
