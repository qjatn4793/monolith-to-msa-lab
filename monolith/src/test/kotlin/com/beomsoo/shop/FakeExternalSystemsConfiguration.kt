package com.beomsoo.shop

import com.beomsoo.shop.notification.application.port.out.NotificationSenderPort
import com.beomsoo.shop.payment.application.port.out.PaymentGatewayPort
import com.beomsoo.shop.payment.application.port.out.PgApprovalRequest
import com.beomsoo.shop.payment.application.port.out.PgApprovalResult
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 통합 테스트에서 외부 시스템(PG, 알림 발송)을 가짜로 바꿔 끼운다.
 * 포트 뒤에 숨어 있어서 빈 하나만 바꾸면 된다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeExternalSystemsConfiguration {

    @Bean
    @Primary
    fun fakePaymentGateway() = FakePaymentGateway()

    @Bean
    @Primary
    fun controllableNotificationSender() = ControllableNotificationSender()
}

/** fake-pg 서버와 같은 규칙: 100만 원을 넘으면 한도 초과로 거절한다. */
class FakePaymentGateway : PaymentGatewayPort {

    override fun approve(request: PgApprovalRequest): PgApprovalResult =
        if (request.amount.amount > APPROVAL_LIMIT) PgApprovalResult.Declined("LIMIT_EXCEEDED")
        else PgApprovalResult.Approved("test-${request.idempotencyKey}")

    companion object {
        const val APPROVAL_LIMIT = 1_000_000L
    }
}

/** 테스트에서 알림 발송 장애를 흉내 낼 수 있다. */
class ControllableNotificationSender : NotificationSenderPort {

    @Volatile
    var failing = false

    val sent = CopyOnWriteArrayList<Pair<String, String>>()

    override fun send(recipient: String, message: String) {
        if (failing) throw IllegalStateException("알림 서버 장애 (테스트)")
        sent += recipient to message
    }
}
