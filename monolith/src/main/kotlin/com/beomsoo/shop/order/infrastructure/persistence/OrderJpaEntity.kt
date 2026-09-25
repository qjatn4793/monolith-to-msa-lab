package com.beomsoo.shop.order.infrastructure.persistence

import com.beomsoo.shop.order.domain.CancelReason
import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.order.domain.OrderLine
import com.beomsoo.shop.order.domain.OrderStatus
import com.beomsoo.shop.shared.domain.Money
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class OrderJpaEntity(
    @Id
    val id: UUID,
    val memberId: UUID,
    @Enumerated(EnumType.STRING)
    var status: OrderStatus,
    @Enumerated(EnumType.STRING)
    var cancelReason: CancelReason?,
    /** 목록 조회에서 주문 상품을 읽지 않고도 합계를 보여주기 위해 저장해 둔다. */
    val totalAmount: Long,
    val orderedAt: Instant,
    /**
     * 주문 상품은 주문 없이 존재할 수 없어서 별도 엔티티가 아니라 값 컬렉션으로 매핑한다.
     * 지연 로딩이라 주문 목록에서 상품까지 읽으면 주문 수만큼 추가 쿼리가 나간다 (N+1). P4에서 다룬다.
     */
    @ElementCollection
    @CollectionTable(name = "order_line", joinColumns = [JoinColumn(name = "order_id")])
    @OrderColumn(name = "line_no")
    val lines: MutableList<OrderLineEmbeddable>,
) {
    @Version
    var version: Long? = null

    fun update(order: Order) {
        status = order.status
        cancelReason = order.cancelReason
    }

    fun toDomain(): Order = Order(
        id = OrderId(id),
        memberId = memberId,
        lines = lines.map { it.toDomain() },
        status = status,
        cancelReason = cancelReason,
        orderedAt = orderedAt,
    )

    companion object {
        fun from(order: Order) = OrderJpaEntity(
            id = order.id.value,
            memberId = order.memberId,
            status = order.status,
            cancelReason = order.cancelReason,
            totalAmount = order.totalAmount.amount,
            orderedAt = order.orderedAt,
            lines = order.lines.map(OrderLineEmbeddable::from).toMutableList(),
        )
    }
}

@Embeddable
class OrderLineEmbeddable(
    val productId: UUID,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
) {
    fun toDomain() = OrderLine(productId, productName, Money(unitPrice), quantity)

    companion object {
        fun from(line: OrderLine) = OrderLineEmbeddable(line.productId, line.productName, line.unitPrice.amount, line.quantity)
    }
}
