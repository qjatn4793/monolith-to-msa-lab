package com.beomsoo.shop.notification.domain

import com.beomsoo.shop.shared.domain.Money
import com.beomsoo.shop.shared.domain.newId
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import java.util.UUID

@JvmInline
value class NotificationId(val value: UUID) {

    companion object {
        fun new(): NotificationId = NotificationId(newId())
    }
}

/** 보낸 알림 한 건의 기록. 같은 주문에 같은 종류의 알림은 한 번만 보낸다. */
class Notification(
    val id: NotificationId,
    val memberId: UUID,
    val orderId: UUID,
    val type: NotificationType,
    val recipient: String,
    val message: String,
    val sentAt: Instant,
) {
    companion object {
        private val WON = NumberFormat.getNumberInstance(Locale.KOREA)

        fun orderConfirmed(memberId: UUID, orderId: UUID, recipient: String, totalAmount: Money, now: Instant) =
            Notification(
                NotificationId.new(), memberId, orderId, NotificationType.ORDER_CONFIRMED, recipient,
                "주문이 완료되었습니다. 결제 금액: ${WON.format(totalAmount.amount)}원",
                now,
            )

        fun orderCancelled(memberId: UUID, orderId: UUID, recipient: String, reason: String, now: Instant) =
            Notification(
                NotificationId.new(), memberId, orderId, NotificationType.ORDER_CANCELLED, recipient,
                when (reason) {
                    "PAYMENT_FAILED" -> "결제가 실패하여 주문이 취소되었습니다."
                    else -> "주문이 취소되었습니다."
                },
                now,
            )
    }
}

enum class NotificationType {
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
}
