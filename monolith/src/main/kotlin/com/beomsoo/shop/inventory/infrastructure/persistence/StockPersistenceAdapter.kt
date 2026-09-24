package com.beomsoo.shop.inventory.infrastructure.persistence

import com.beomsoo.shop.inventory.domain.Stock
import com.beomsoo.shop.inventory.domain.StockRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StockPersistenceAdapter(
    private val jpaRepository: StockJpaRepository,
) : StockRepository {

    override fun save(stock: Stock) {
        val entity = jpaRepository.findByIdOrNull(stock.productId)
        if (entity == null) {
            jpaRepository.save(StockJpaEntity.from(stock))
        } else {
            entity.update(stock)
        }
    }

    override fun saveAll(stocks: Collection<Stock>) = stocks.forEach(::save)

    override fun findByProductId(productId: UUID): Stock? = jpaRepository.findByIdOrNull(productId)?.toDomain()

    override fun findAllByProductIds(productIds: Collection<UUID>): List<Stock> =
        jpaRepository.findAllById(productIds).map { it.toDomain() }
}
