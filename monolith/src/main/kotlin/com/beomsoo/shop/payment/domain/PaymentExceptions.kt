package com.beomsoo.shop.payment.domain

import com.beomsoo.shop.shared.domain.BusinessException
import com.beomsoo.shop.shared.domain.ErrorType

class PaymentNotFoundException(id: PaymentId) :
    BusinessException(ErrorType.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다: $id")

class PaymentAlreadyCompletedException(id: PaymentId, status: PaymentStatus) :
    BusinessException(ErrorType.CONFLICT, "PAYMENT_ALREADY_COMPLETED", "이미 처리된 결제입니다: $id ($status)")
