package com.beomsoo.shop.payment.application.port.out

import com.beomsoo.shop.shared.domain.Money
import java.util.UUID

/** 아웃바운드 포트: 외부 결제 대행사(PG) */
interface PaymentGatewayPort {

    fun approve(request: PgApprovalRequest): PgApprovalResult
}

data class PgApprovalRequest(
    /** PG가 중복 결제를 막는 데 쓰는 키. 결제 ID를 그대로 쓴다. */
    val idempotencyKey: String,
    val orderId: UUID,
    val amount: Money,
)

sealed interface PgApprovalResult {

    data class Approved(val transactionId: String) : PgApprovalResult

    /** PG가 결제를 거절했다 (한도 초과 등). 결과가 확실하다. */
    data class Declined(val reason: String) : PgApprovalResult

    /**
     * PG와 통신하지 못했다 (타임아웃, 5xx 등). 결과를 모른다.
     * PG 쪽에서는 승인됐을 수도 있다는 점에 주의해야 한다 (ADR-0012).
     */
    data class Unavailable(val reason: String) : PgApprovalResult
}
