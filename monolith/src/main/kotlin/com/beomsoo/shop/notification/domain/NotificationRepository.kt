package com.beomsoo.shop.notification.domain

import java.util.UUID

interface NotificationRepository {

    fun save(notification: Notification)

    fun existsByOrderIdAndType(orderId: UUID, type: NotificationType): Boolean
}
