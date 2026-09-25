package com.beomsoo.shop.notification.application.port.`in`

import java.util.UUID

/**
 * 주문 결과를 회원에게 알린다.
 *
 * 이벤트는 "최소 한 번" 전달된다. 이벤트 발행 저장소가 실패한 전달을 다시 시도하면 같은 이벤트가 두 번 올 수 있다.
 * 그래서 이미 보낸 알림이면 다시 보내지 않는다 (멱등성).
 */
interface NotifyOrderResultUseCase {

    fun notifyConfirmed(orderId: UUID, memberId: UUID, totalAmount: Long)

    fun notifyCancelled(orderId: UUID, memberId: UUID, reason: String)
}
