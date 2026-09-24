package com.beomsoo.shop.catalog.application.service

import com.beomsoo.shop.catalog.application.port.`in`.GetProductQuery
import com.beomsoo.shop.catalog.application.port.out.ProductListPort
import com.beomsoo.shop.catalog.domain.Product
import com.beomsoo.shop.catalog.domain.ProductId
import com.beomsoo.shop.catalog.domain.ProductNotFoundException
import com.beomsoo.shop.catalog.domain.ProductRepository
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProductQueryService(
    private val productRepository: ProductRepository,
    private val productListPort: ProductListPort,
) : GetProductQuery {

    override fun getProduct(id: ProductId): Product = productRepository.findById(id) ?: throw ProductNotFoundException(id)

    override fun findProducts(ids: Collection<ProductId>): List<Product> =
        if (ids.isEmpty()) emptyList() else productRepository.findAllByIds(ids)

    override fun getProducts(query: PageQuery): PageResult<Product> = productListPort.findPage(query)
}
