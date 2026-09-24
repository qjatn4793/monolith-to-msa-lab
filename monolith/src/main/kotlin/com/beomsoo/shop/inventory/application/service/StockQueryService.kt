package com.beomsoo.shop.inventory.application.service

import com.beomsoo.shop.inventory.application.port.`in`.GetStockQuery
import com.beomsoo.shop.inventory.domain.Stock
import com.beomsoo.shop.inventory.domain.StockNotFoundException
import com.beomsoo.shop.inventory.domain.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StockQueryService(
    private val stockRepository: StockRepository,
) : GetStockQuery {

    override fun getStock(productId: UUID): Stock =
        stockRepository.findByProductId(productId) ?: throw StockNotFoundException(productId)
}
