package com.beomsoo.shop.order.infrastructure.web

import com.beomsoo.shop.order.domain.Order
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.util.UUID

data class PlaceOrderRequest(
    val memberId: UUID,
    @field:NotEmpty @field:Valid val items: List<Item>,
) {
    data class Item(
        val productId: UUID,
        @field:Positive val quantity: Int,
    )
}

data class PlaceOrderResponse(
    val id: UUID,
    /** CONFIRMED(결제 완료) 또는 CANCELLED(결제 실패) */
    val status: String,
)

data class OrderResponse(
    val id: UUID,
    val memberId: UUID,
    val status: String,
    val cancelReason: String?,
    val totalAmount: Long,
    val orderedAt: Instant,
    val lines: List<Line>,
) {
    data class Line(
        val productId: UUID,
        val productName: String,
        val unitPrice: Long,
        val quantity: Int,
        val amount: Long,
    )

    companion object {
        fun from(order: Order) = OrderResponse(
            id = order.id.value,
            memberId = order.memberId,
            status = order.status.name,
            cancelReason = order.cancelReason?.name,
            totalAmount = order.totalAmount.amount,
            orderedAt = order.orderedAt,
            lines = order.lines.map {
                Line(it.productId, it.productName, it.unitPrice.amount, it.quantity, it.amount.amount)
            },
        )
    }
}
