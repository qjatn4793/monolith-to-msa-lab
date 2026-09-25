package com.beomsoo.shop.notification.infrastructure.listener

import com.beomsoo.shop.notification.application.port.`in`.NotifyOrderResultUseCase
import com.beomsoo.shop.order.api.OrderCancelledEvent
import com.beomsoo.shop.order.api.OrderConfirmedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * 인바운드 어댑터: 다른 모듈의 이벤트를 받아 유스케이스를 호출한다.
 * 컨트롤러(HTTP), 파사드(동기 호출)와 같은 계열이고, 들어오는 수단만 이벤트다.
 *
 * @ApplicationModuleListener = @Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener(AFTER_COMMIT)
 * - 발행한 트랜잭션(주문 확정)이 커밋된 뒤에만 실행된다
 * - 다른 스레드에서 자기 트랜잭션으로 실행된다. 여기서 실패해도 주문은 영향을 받지 않는다
 * - 실패하면 EVENT_PUBLICATION 테이블에 미완료로 남고, 나중에 다시 처리할 수 있다 (ADR-0011)
 */
@Component
class OrderEventListener(
    private val notifyOrderResultUseCase: NotifyOrderResultUseCase,
) {

    @ApplicationModuleListener
    fun on(event: OrderConfirmedEvent) {
        notifyOrderResultUseCase.notifyConfirmed(event.orderId, event.memberId, event.totalAmount)
    }

    @ApplicationModuleListener
    fun on(event: OrderCancelledEvent) {
        notifyOrderResultUseCase.notifyCancelled(event.orderId, event.memberId, event.reason)
    }
}
