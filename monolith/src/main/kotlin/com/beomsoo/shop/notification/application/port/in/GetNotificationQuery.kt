package com.beomsoo.shop.notification.application.port.`in`

import com.beomsoo.shop.notification.domain.Notification
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import java.util.UUID

interface GetNotificationQuery {

    fun getNotificationsOfMember(memberId: UUID, query: PageQuery): PageResult<Notification>
}
