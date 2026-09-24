package com.beomsoo.shop.catalog.application.port.`in`

import com.beomsoo.shop.catalog.domain.Product
import com.beomsoo.shop.catalog.domain.ProductId
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult

interface GetProductQuery {

    fun getProduct(id: ProductId): Product

    fun findProducts(ids: Collection<ProductId>): List<Product>

    fun getProducts(query: PageQuery): PageResult<Product>
}
