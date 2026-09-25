package com.beomsoo.shop.notification.infrastructure.adapter

import com.beomsoo.shop.notification.application.port.out.NotificationSenderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 가짜 발송 수단. 실제 메일이나 푸시 대신 로그를 남긴다. */
@Component
class LogNotificationSender : NotificationSenderPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(recipient: String, message: String) {
        log.info("[알림 발송] to={}, message={}", recipient, message)
    }
}
