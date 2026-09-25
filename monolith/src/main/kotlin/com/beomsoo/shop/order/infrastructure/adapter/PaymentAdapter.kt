package com.beomsoo.shop.order.infrastructure.adapter

import com.beomsoo.shop.order.application.port.out.PaymentResult
import com.beomsoo.shop.order.application.port.out.RequestPaymentPort
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.payment.api.PaymentFacade
import com.beomsoo.shop.shared.domain.Money
import org.springframework.stereotype.Component
import com.beomsoo.shop.payment.api.PaymentResult as PaymentApiResult

/** 아웃바운드 어댑터: order → payment */
@Component
class PaymentAdapter(
    private val paymentFacade: PaymentFacade,
) : RequestPaymentPort {

    override fun pay(orderId: OrderId, amount: Money): PaymentResult =
        when (val result = paymentFacade.pay(orderId.value, amount.amount)) {
            is PaymentApiResult.Approved -> PaymentResult.Paid
            is PaymentApiResult.Failed -> PaymentResult.Failed(result.reason)
        }
}
