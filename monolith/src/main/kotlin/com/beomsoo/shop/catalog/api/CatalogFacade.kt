package com.beomsoo.shop.catalog.api

import java.util.UUID

/** catalog 모듈이 다른 모듈에 공개하는 동기 API */
interface CatalogFacade {

    /** 요청한 ID 중 존재하는 상품만 돌려준다. 없는 ID는 결과에서 빠진다. */
    fun findProducts(productIds: Collection<UUID>): List<ProductInfo>
}

data class ProductInfo(
    val id: UUID,
    val name: String,
    /** 원 단위 가격 */
    val price: Long,
    val onSale: Boolean,
)
