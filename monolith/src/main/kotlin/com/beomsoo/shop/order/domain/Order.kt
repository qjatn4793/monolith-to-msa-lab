package com.beomsoo.shop.order.domain

import com.beomsoo.shop.order.domain.event.OrderCancelled
import com.beomsoo.shop.order.domain.event.OrderConfirmed
import com.beomsoo.shop.shared.domain.InvalidInputException
import com.beomsoo.shop.shared.domain.Money
import com.beomsoo.shop.shared.domain.sum
import java.time.Instant
import java.util.UUID

/**
 * 주문 애그리거트 루트. 주문 상품(OrderLine)은 이 애그리거트 안에서만 만들어지고 바뀐다.
 *
 * 주문자는 member 모듈의 회원이지만 Member 객체가 아니라 ID(UUID)로만 참조한다 (ADR-0004).
 * 그래서 주문을 불러올 때 회원 테이블을 JOIN 할 일이 없다.
 *
 * 상태 흐름:
 *   PENDING ──(결제 승인)──▶ CONFIRMED
 *      └────(결제 실패, 취소)──▶ CANCELLED
 */
class Order(
    val id: OrderId,
    val memberId: UUID,
    lines: List<OrderLine>,
    status: OrderStatus,
    cancelReason: CancelReason?,
    val orderedAt: Instant,
) {
    val lines: List<OrderLine> = validateLines(lines)

    var status: OrderStatus = status
        private set

    var cancelReason: CancelReason? = cancelReason
        private set

    val totalAmount: Money get() = lines.map { it.amount }.sum()

    /** 결제가 끝나 주문을 확정한다. 상태를 바꾸고, 무슨 일이 일어났는지를 도메인 이벤트로 돌려준다. */
    fun confirm(now: Instant): OrderConfirmed {
        if (status != OrderStatus.PENDING) throw OrderNotConfirmableException(id, status)
        status = OrderStatus.CONFIRMED
        return OrderConfirmed(id, memberId, totalAmount, now)
    }

    fun cancel(reason: CancelReason, now: Instant): OrderCancelled {
        if (status != OrderStatus.PENDING) throw OrderNotCancellableException(id, status)
        status = OrderStatus.CANCELLED
        cancelReason = reason
        return OrderCancelled(id, memberId, reason, now)
    }

    companion object {
        private const val MAX_LINES = 50

        fun place(memberId: UUID, lines: List<OrderLine>, now: Instant): Order =
            Order(OrderId.new(), memberId, lines, OrderStatus.PENDING, null, now)

        private fun validateLines(lines: List<OrderLine>): List<OrderLine> {
            if (lines.isEmpty()) throw InvalidInputException("주문 상품이 없습니다.")
            if (lines.size > MAX_LINES) throw InvalidInputException("한 번에 주문할 수 있는 상품은 ${MAX_LINES}종류까지입니다.")
            if (lines.distinctBy { it.productId }.size != lines.size) {
                throw InvalidInputException("같은 상품이 중복으로 들어 있습니다. 수량을 합쳐서 주문해 주세요.")
            }
            return lines.toList()
        }
    }
}

enum class OrderStatus {
    /** 재고를 예약했고 결제를 기다린다 */
    PENDING,

    /** 결제가 끝났다 */
    CONFIRMED,

    /** 결제 실패나 요청으로 취소됐다. 예약한 재고는 해제된다 */
    CANCELLED,
}

enum class CancelReason {
    PAYMENT_FAILED,
    REQUESTED,
}
