package com.beomsoo.shop.inventory.application.service

import com.beomsoo.shop.inventory.application.port.`in`.ReceiveStockUseCase
import com.beomsoo.shop.inventory.application.port.`in`.ReservationResult
import com.beomsoo.shop.inventory.application.port.`in`.ReserveStockUseCase
import com.beomsoo.shop.inventory.application.port.`in`.Shortage
import com.beomsoo.shop.inventory.application.port.`in`.StockQuantity
import com.beomsoo.shop.inventory.domain.Stock
import com.beomsoo.shop.inventory.domain.StockNotFoundException
import com.beomsoo.shop.inventory.domain.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class StockCommandService(
    private val stockRepository: StockRepository,
) : ReceiveStockUseCase, ReserveStockUseCase {

    override fun receive(productId: UUID, quantity: Int) {
        val stock = stockRepository.findByProductId(productId) ?: Stock.empty(productId)
        stock.receive(quantity)
        stockRepository.save(stock)
    }

    override fun reserve(items: List<StockQuantity>): ReservationResult {
        val requested = merge(items)
        val stocks = stockRepository.findAllByProductIds(requested.keys).associateBy { it.productId }

        // 먼저 전부 검사하고, 하나라도 부족하면 아무것도 바꾸지 않고 돌려준다.
        val shortages = requested.mapNotNull { (productId, quantity) ->
            val stock = stocks[productId]
            if (stock != null && stock.canReserve(quantity)) null
            else Shortage(productId, quantity, stock?.available ?: 0)
        }
        if (shortages.isNotEmpty()) return ReservationResult.Rejected(shortages)

        requested.forEach { (productId, quantity) -> stocks.getValue(productId).reserve(quantity) }
        stockRepository.saveAll(stocks.values)
        return ReservationResult.Reserved
    }

    override fun release(items: List<StockQuantity>) {
        val requested = merge(items)
        val stocks = stockRepository.findAllByProductIds(requested.keys).associateBy { it.productId }
        requested.forEach { (productId, quantity) ->
            val stock = stocks[productId] ?: throw StockNotFoundException(productId)
            stock.release(quantity)
        }
        stockRepository.saveAll(stocks.values)
    }

    /** 같은 상품이 여러 번 들어오면 수량을 합친다. 정렬해두면 여러 행을 갱신하는 순서가 항상 같아진다. */
    private fun merge(items: List<StockQuantity>): Map<UUID, Int> =
        items.groupBy { it.productId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }
            .toSortedMap()
}
