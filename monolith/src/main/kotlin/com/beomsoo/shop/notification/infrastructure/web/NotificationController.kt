package com.beomsoo.shop.notification.infrastructure.web

import com.beomsoo.shop.notification.application.port.`in`.GetNotificationQuery
import com.beomsoo.shop.notification.domain.Notification
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val getNotificationQuery: GetNotificationQuery,
) {

    @GetMapping
    fun listOfMember(
        @RequestParam memberId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<NotificationResponse> =
        getNotificationQuery.getNotificationsOfMember(memberId, PageQuery(page, size)).map(NotificationResponse::from)
}

data class NotificationResponse(
    val id: UUID,
    val orderId: UUID,
    val type: String,
    val recipient: String,
    val message: String,
    val sentAt: Instant,
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(n.id.value, n.orderId, n.type.name, n.recipient, n.message, n.sentAt)
    }
}
