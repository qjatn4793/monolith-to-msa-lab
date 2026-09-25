package com.beomsoo.shop.inventory.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import java.util.UUID

/**
 * 상품 하나의 재고. 애그리거트 루트이며 productId로 식별한다.
 *
 * productId는 catalog 모듈의 상품 ID지만, 다른 모듈의 도메인 타입(ProductId)을 쓸 수 없으므로 UUID로 보관한다.
 * 다른 모듈의 ID는 항상 UUID로만 들고 있는다 (ADR-0004).
 *
 *   available : 지금 주문할 수 있는 수량
 *   reserved  : 주문이 잡아둔 수량. 결제가 끝나면 빠지고(confirm), 주문이 취소되면 available로 돌아간다(release)
 */
class Stock(
    val productId: UUID,
    available: Int,
    reserved: Int,
) {
    var available: Int = available
        private set

    var reserved: Int = reserved
        private set

    init {
        check(available >= 0 && reserved >= 0) { "재고 수량은 음수일 수 없습니다: available=$available, reserved=$reserved" }
    }

    /** 입고 */
    fun receive(quantity: Int) {
        validateQuantity(quantity)
        available = Math.addExact(available, quantity)
    }

    fun canReserve(quantity: Int): Boolean = quantity in 1..available

    /** 주문을 위해 재고를 잡아둔다 */
    fun reserve(quantity: Int) {
        validateQuantity(quantity)
        if (!canReserve(quantity)) throw InsufficientStockException(productId, quantity, available)
        available -= quantity
        reserved += quantity
    }

    /** 잡아둔 재고를 확정 차감한다 (결제 완료). 예약 수량에서 빠지고 가용 수량으로 돌아가지 않는다. */
    fun confirm(quantity: Int) {
        validateQuantity(quantity)
        if (quantity > reserved) throw ReservedStockExceededException(productId, quantity, reserved)
        reserved -= quantity
    }

    /** 잡아둔 재고를 되돌린다 (주문 취소) */
    fun release(quantity: Int) {
        validateQuantity(quantity)
        if (quantity > reserved) throw ReservedStockExceededException(productId, quantity, reserved)
        reserved -= quantity
        available += quantity
    }

    companion object {
        fun empty(productId: UUID): Stock = Stock(productId, available = 0, reserved = 0)

        private fun validateQuantity(quantity: Int) {
            if (quantity <= 0) throw InvalidInputException("수량은 1 이상이어야 합니다: $quantity")
        }
    }
}
