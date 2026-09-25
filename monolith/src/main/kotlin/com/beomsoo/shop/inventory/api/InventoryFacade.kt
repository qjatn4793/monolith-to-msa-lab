package com.beomsoo.shop.inventory.api

import java.util.UUID

/** inventory 모듈이 다른 모듈에 공개하는 동기 API */
interface InventoryFacade {

    /** 하나라도 부족하면 아무것도 예약하지 않고 Rejected를 돌려준다. */
    fun reserve(items: List<StockItem>): ReserveStockResult

    /** 예약을 확정 차감한다 (결제 완료) */
    fun confirm(items: List<StockItem>)

    /** 예약을 해제한다 (주문 취소) */
    fun release(items: List<StockItem>)
}

data class StockItem(
    val productId: UUID,
    val quantity: Int,
)

sealed interface ReserveStockResult {

    data object Reserved : ReserveStockResult

    data class Rejected(val insufficientProductIds: List<UUID>) : ReserveStockResult
}
