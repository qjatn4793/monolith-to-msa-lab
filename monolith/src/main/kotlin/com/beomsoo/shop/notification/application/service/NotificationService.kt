package com.beomsoo.shop.notification.application.service

import com.beomsoo.shop.notification.application.port.`in`.GetNotificationQuery
import com.beomsoo.shop.notification.application.port.`in`.NotifyOrderResultUseCase
import com.beomsoo.shop.notification.application.port.out.LoadRecipientPort
import com.beomsoo.shop.notification.application.port.out.NotificationListPort
import com.beomsoo.shop.notification.application.port.out.NotificationSenderPort
import com.beomsoo.shop.notification.domain.Notification
import com.beomsoo.shop.notification.domain.NotificationRepository
import com.beomsoo.shop.notification.domain.NotificationType
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import com.beomsoo.shop.shared.domain.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * 알림 유스케이스.
 *
 * 지금의 발송 수단(LogNotificationSender)은 로그만 남기는 가짜라 트랜잭션 안에서 보내도 문제가 없다.
 * 실제 메일 서버를 붙이면 결제와 마찬가지로 외부 호출을 트랜잭션 밖으로 빼야 한다 (ADR-0006).
 */
@Service
@Transactional
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val notificationListPort: NotificationListPort,
    private val loadRecipientPort: LoadRecipientPort,
    private val sender: NotificationSenderPort,
    private val clock: Clock,
) : NotifyOrderResultUseCase, GetNotificationQuery {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun notifyConfirmed(orderId: UUID, memberId: UUID, totalAmount: Long) =
        notifyOnce(orderId, memberId, NotificationType.ORDER_CONFIRMED) { recipient ->
            Notification.orderConfirmed(memberId, orderId, recipient, Money(totalAmount), clock.instant())
        }

    override fun notifyCancelled(orderId: UUID, memberId: UUID, reason: String) =
        notifyOnce(orderId, memberId, NotificationType.ORDER_CANCELLED) { recipient ->
            Notification.orderCancelled(memberId, orderId, recipient, reason, clock.instant())
        }

    @Transactional(readOnly = true)
    override fun getNotificationsOfMember(memberId: UUID, query: PageQuery): PageResult<Notification> =
        notificationListPort.findByMemberId(memberId, query)

    private fun notifyOnce(orderId: UUID, memberId: UUID, type: NotificationType, create: (recipient: String) -> Notification) {
        if (notificationRepository.existsByOrderIdAndType(orderId, type)) {
            log.info("이미 보낸 알림이라 건너뜀: order={}, type={}", orderId, type)
            return
        }
        val recipient = loadRecipientPort.loadEmail(memberId)
            ?: throw IllegalStateException("알림을 받을 회원의 연락처가 없습니다: member=$memberId")

        val notification = create(recipient)
        sender.send(notification.recipient, notification.message)
        notificationRepository.save(notification)
    }
}
