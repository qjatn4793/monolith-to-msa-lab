package com.beomsoo.shop.catalog.domain

/**
 * 상품 애그리거트 저장소. 애그리거트를 저장하고 ID로 불러오는 일만 한다.
 * 화면용 목록 조회는 application/port/out/ProductListPort가 맡는다.
 */
interface ProductRepository {

    fun save(product: Product)

    fun findById(id: ProductId): Product?

    fun findAllByIds(ids: Collection<ProductId>): List<Product>
}
