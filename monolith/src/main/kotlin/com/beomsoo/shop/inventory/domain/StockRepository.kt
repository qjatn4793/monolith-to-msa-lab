package com.beomsoo.shop.inventory.domain

import java.util.UUID

interface StockRepository {

    fun save(stock: Stock)

    fun saveAll(stocks: Collection<Stock>)

    fun findByProductId(productId: UUID): Stock?

    fun findAllByProductIds(productIds: Collection<UUID>): List<Stock>
}
