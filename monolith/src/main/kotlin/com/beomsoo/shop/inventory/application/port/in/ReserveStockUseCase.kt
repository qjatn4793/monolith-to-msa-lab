package com.beomsoo.shop.inventory.application.port.`in`

import java.util.UUID

interface ReserveStockUseCase {

    /**
     * 여러 상품의 재고를 한 번에 예약한다. 하나라도 부족하면 아무것도 예약하지 않는다 (all-or-nothing).
     *
     * 재고 부족은 예외가 아니라 결과값(Rejected)으로 돌려준다.
     * 호출하는 쪽(order)의 트랜잭션 안에서 예외가 이 경계를 넘으면, 호출하는 쪽이 예외를 잡더라도
     * 트랜잭션 전체가 rollback-only로 표시되어 커밋할 수 없게 된다.
     */
    fun reserve(items: List<StockQuantity>): ReservationResult

    /** 예약을 확정 차감한다 (결제 완료) */
    fun confirm(items: List<StockQuantity>)

    /** 예약을 해제한다 (주문 취소) */
    fun release(items: List<StockQuantity>)
}

data class StockQuantity(
    val productId: UUID,
    val quantity: Int,
)

sealed interface ReservationResult {

    data object Reserved : ReservationResult

    data class Rejected(val shortages: List<Shortage>) : ReservationResult
}

data class Shortage(
    val productId: UUID,
    val requested: Int,
    val available: Int,
)
