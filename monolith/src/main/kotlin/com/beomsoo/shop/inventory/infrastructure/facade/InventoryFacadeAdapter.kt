package com.beomsoo.shop.inventory.infrastructure.facade

import com.beomsoo.shop.inventory.api.InventoryFacade
import com.beomsoo.shop.inventory.api.ReserveStockResult
import com.beomsoo.shop.inventory.api.StockItem
import com.beomsoo.shop.inventory.application.port.`in`.ReservationResult
import com.beomsoo.shop.inventory.application.port.`in`.ReserveStockUseCase
import com.beomsoo.shop.inventory.application.port.`in`.StockQuantity
import org.springframework.stereotype.Component

@Component
class InventoryFacadeAdapter(
    private val reserveStockUseCase: ReserveStockUseCase,
) : InventoryFacade {

    override fun reserve(items: List<StockItem>): ReserveStockResult =
        when (val result = reserveStockUseCase.reserve(items.toQuantities())) {
            ReservationResult.Reserved -> ReserveStockResult.Reserved
            is ReservationResult.Rejected -> ReserveStockResult.Rejected(result.shortages.map { it.productId })
        }

    override fun release(items: List<StockItem>) = reserveStockUseCase.release(items.toQuantities())

    private fun List<StockItem>.toQuantities() = map { StockQuantity(it.productId, it.quantity) }
}
