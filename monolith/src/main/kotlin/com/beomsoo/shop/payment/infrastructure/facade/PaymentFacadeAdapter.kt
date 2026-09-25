package com.beomsoo.shop.payment.infrastructure.facade

import com.beomsoo.shop.payment.api.PaymentFacade
import com.beomsoo.shop.payment.api.PaymentResult
import com.beomsoo.shop.payment.application.port.`in`.PaymentOutcome
import com.beomsoo.shop.payment.application.port.`in`.RequestPaymentCommand
import com.beomsoo.shop.payment.application.port.`in`.RequestPaymentUseCase
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PaymentFacadeAdapter(
    private val requestPaymentUseCase: RequestPaymentUseCase,
) : PaymentFacade {

    override fun pay(orderId: UUID, amount: Long): PaymentResult =
        when (val outcome = requestPaymentUseCase.pay(RequestPaymentCommand(orderId, amount))) {
            is PaymentOutcome.Approved -> PaymentResult.Approved(outcome.paymentId.value)
            is PaymentOutcome.Failed -> PaymentResult.Failed(outcome.paymentId.value, outcome.reason)
        }
}
