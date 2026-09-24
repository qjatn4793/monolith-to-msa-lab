package com.beomsoo.shop.order.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import com.beomsoo.shop.shared.domain.Money
import java.util.UUID

/**
 * 주문 상품 한 줄. 주문 시점의 상품명과 가격을 복사해 둔다(스냅샷).
 * 나중에 catalog에서 가격이 바뀌어도 이미 한 주문의 금액은 바뀌지 않는다.
 */
data class OrderLine(
    val productId: UUID,
    val productName: String,
    val unitPrice: Money,
    val quantity: Int,
) {
    init {
        if (quantity !in 1..MAX_QUANTITY) throw InvalidInputException("주문 수량은 1개 이상 ${MAX_QUANTITY}개 이하여야 합니다: $quantity")
    }

    val amount: Money get() = unitPrice * quantity

    companion object {
        const val MAX_QUANTITY = 999
    }
}
