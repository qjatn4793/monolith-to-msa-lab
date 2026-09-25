package com.beomsoo.shop.notification.application.port.out

import com.beomsoo.shop.notification.domain.Notification
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import java.util.UUID

/** 받는 사람의 연락처. 구현은 member 모듈의 api를 호출한다. */
interface LoadRecipientPort {

    fun loadEmail(memberId: UUID): String?
}

/** 실제 발송 수단 (메일, 푸시 등) */
interface NotificationSenderPort {

    fun send(recipient: String, message: String)
}

interface NotificationListPort {

    fun findByMemberId(memberId: UUID, query: PageQuery): PageResult<Notification>
}
