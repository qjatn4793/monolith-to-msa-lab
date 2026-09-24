package com.beomsoo.shop.catalog.infrastructure.persistence

import com.beomsoo.shop.catalog.application.port.out.ProductListPort
import com.beomsoo.shop.catalog.domain.Product
import com.beomsoo.shop.catalog.domain.ProductId
import com.beomsoo.shop.catalog.domain.ProductRepository
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductPersistenceAdapter(
    private val jpaRepository: ProductJpaRepository,
) : ProductRepository, ProductListPort {

    override fun save(product: Product) {
        val entity = jpaRepository.findByIdOrNull(product.id.value)
        if (entity == null) {
            jpaRepository.save(ProductJpaEntity.from(product))
        } else {
            entity.update(product)
        }
    }

    override fun findById(id: ProductId): Product? = jpaRepository.findByIdOrNull(id.value)?.toDomain()

    override fun findAllByIds(ids: Collection<ProductId>): List<Product> =
        jpaRepository.findAllById(ids.map { it.value }).map { it.toDomain() }

    /** UUID v7은 생성 시각 순으로 정렬되므로, PK 역순이 곧 최신 등록순이다. */
    override fun findPage(query: PageQuery): PageResult<Product> {
        val page = jpaRepository.findAll(PageRequest.of(query.page, query.size, Sort.by(Sort.Direction.DESC, "id")))
        return PageResult(page.content.map { it.toDomain() }, query.page, query.size, page.totalElements)
    }
}
