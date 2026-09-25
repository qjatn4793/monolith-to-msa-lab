package com.beomsoo.shop.notification.application

import com.beomsoo.shop.notification.application.port.out.LoadRecipientPort
import com.beomsoo.shop.notification.application.port.out.NotificationListPort
import com.beomsoo.shop.notification.application.port.out.NotificationSenderPort
import com.beomsoo.shop.notification.application.service.NotificationService
import com.beomsoo.shop.notification.domain.Notification
import com.beomsoo.shop.notification.domain.NotificationRepository
import com.beomsoo.shop.notification.domain.NotificationType
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class NotificationServiceTest {

    private val saved = mutableListOf<Notification>()
    private val repository = object : NotificationRepository, NotificationListPort {
        override fun save(notification: Notification) { saved += notification }
        override fun existsByOrderIdAndType(orderId: UUID, type: NotificationType) =
            saved.any { it.orderId == orderId && it.type == type }
        override fun findByMemberId(memberId: UUID, query: PageQuery) = PageResult(saved.toList(), 0, 20, saved.size.toLong())
    }
    private val sent = mutableListOf<String>()
    private val sender = object : NotificationSenderPort {
        override fun send(recipient: String, message: String) { sent += message }
    }
    private val recipients = object : LoadRecipientPort {
        override fun loadEmail(memberId: UUID) = "user@example.com"
    }
    private val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)

    private val service = NotificationService(repository, repository, recipients, sender, clock)

    @Test
    fun `같은 이벤트가 두 번 전달돼도 알림은 한 번만 보낸다`() {
        val orderId = UUID.randomUUID()
        val memberId = UUID.randomUUID()

        service.notifyConfirmed(orderId, memberId, 12_000)
        service.notifyConfirmed(orderId, memberId, 12_000)

        assertEquals(listOf("주문이 완료되었습니다. 결제 금액: 12,000원"), sent)
        assertEquals(1, saved.size)
    }
}
