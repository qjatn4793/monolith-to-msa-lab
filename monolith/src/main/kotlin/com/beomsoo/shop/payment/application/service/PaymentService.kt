package com.beomsoo.shop.payment.application.service

import com.beomsoo.shop.payment.application.port.`in`.GetPaymentQuery
import com.beomsoo.shop.payment.application.port.`in`.PaymentOutcome
import com.beomsoo.shop.payment.application.port.`in`.RequestPaymentCommand
import com.beomsoo.shop.payment.application.port.`in`.RequestPaymentUseCase
import com.beomsoo.shop.payment.application.port.out.PaymentGatewayPort
import com.beomsoo.shop.payment.application.port.out.PgApprovalRequest
import com.beomsoo.shop.payment.application.port.out.PgApprovalResult
import com.beomsoo.shop.payment.domain.Payment
import com.beomsoo.shop.payment.domain.PaymentId
import com.beomsoo.shop.payment.domain.PaymentNotFoundException
import com.beomsoo.shop.payment.domain.PaymentRepository
import com.beomsoo.shop.shared.domain.Money
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.util.UUID

/**
 * 결제 유스케이스. 트랜잭션 경계를 @Transactional이 아니라 TransactionOperations로 코드에 드러낸다.
 *
 *   [TX] 결제 요청 기록 (REQUESTED)
 *        PG 호출            ← 트랜잭션 밖. PG가 느려도 DB 커넥션을 붙잡지 않는다
 *   [TX] 결과 기록 (APPROVED / FAILED)
 */
@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGatewayPort,
    private val tx: TransactionOperations,
    private val clock: Clock,
) : RequestPaymentUseCase, GetPaymentQuery {

    override fun pay(command: RequestPaymentCommand): PaymentOutcome {
        val payment = tx.execute {
            Payment.request(command.orderId, Money(command.amount), clock.instant()).also(paymentRepository::save)
        }!!

        val result = paymentGateway.approve(PgApprovalRequest(payment.id.toString(), payment.orderId, payment.amount))

        return tx.execute {
            val requested = paymentRepository.findById(payment.id) ?: throw PaymentNotFoundException(payment.id)
            val outcome = when (result) {
                is PgApprovalResult.Approved -> {
                    requested.approve(result.transactionId, clock.instant())
                    PaymentOutcome.Approved(requested.id)
                }
                is PgApprovalResult.Declined -> {
                    requested.fail("DECLINED: ${result.reason}", clock.instant())
                    PaymentOutcome.Failed(requested.id, result.reason)
                }
                is PgApprovalResult.Unavailable -> {
                    requested.fail("PG_UNAVAILABLE: ${result.reason}", clock.instant())
                    PaymentOutcome.Failed(requested.id, "PG_UNAVAILABLE")
                }
            }
            paymentRepository.save(requested)
            outcome
        }!!
    }

    override fun getPayment(id: PaymentId): Payment =
        tx.execute { paymentRepository.findById(id) } ?: throw PaymentNotFoundException(id)

    override fun getPaymentsOfOrder(orderId: UUID): List<Payment> =
        tx.execute { paymentRepository.findByOrderId(orderId) }!!
}
