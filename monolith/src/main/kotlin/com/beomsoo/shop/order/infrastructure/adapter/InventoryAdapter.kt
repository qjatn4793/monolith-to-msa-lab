package com.beomsoo.shop.order.infrastructure.adapter

import com.beomsoo.shop.inventory.api.InventoryFacade
import com.beomsoo.shop.inventory.api.ReserveStockResult
import com.beomsoo.shop.inventory.api.StockItem
import com.beomsoo.shop.order.application.port.out.StockPort
import com.beomsoo.shop.order.application.port.out.StockReservation
import com.beomsoo.shop.order.domain.OrderLine
import org.springframework.stereotype.Component

/** 아웃바운드 어댑터: order → inventory */
@Component
class InventoryAdapter(
    private val inventoryFacade: InventoryFacade,
) : StockPort {

    override fun reserve(lines: List<OrderLine>): StockReservation =
        when (val result = inventoryFacade.reserve(lines.toStockItems())) {
            ReserveStockResult.Reserved -> StockReservation.Reserved
            is ReserveStockResult.Rejected -> StockReservation.OutOfStock(result.insufficientProductIds)
        }

    override fun confirm(lines: List<OrderLine>) = inventoryFacade.confirm(lines.toStockItems())

    override fun release(lines: List<OrderLine>) = inventoryFacade.release(lines.toStockItems())

    private fun List<OrderLine>.toStockItems() = map { StockItem(it.productId, it.quantity) }
}
