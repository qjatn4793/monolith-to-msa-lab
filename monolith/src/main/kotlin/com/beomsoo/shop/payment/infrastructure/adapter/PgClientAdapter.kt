package com.beomsoo.shop.payment.infrastructure.adapter

import com.beomsoo.shop.payment.application.port.out.PaymentGatewayPort
import com.beomsoo.shop.payment.application.port.out.PgApprovalRequest
import com.beomsoo.shop.payment.application.port.out.PgApprovalResult
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import java.net.http.HttpClient

/**
 * 아웃바운드 어댑터: 외부 PG를 HTTP로 호출한다.
 *
 * 호출 직전에 DB 트랜잭션이 열려 있는지 검사한다 (ADR-0006).
 * PG 응답을 기다리는 동안 DB 커넥션을 붙잡으면, PG가 느려질 때 커넥션 풀이 고갈되어
 * 결제와 무관한 API까지 멈춘다. 이 규칙은 코드 리뷰로는 놓치기 쉬워서 실행 시점에 막는다.
 */
@Component
@EnableConfigurationProperties(PgProperties::class)
class PgClientAdapter(
    properties: PgProperties,
    restClientBuilder: RestClient.Builder,
) : PaymentGatewayPort {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build())
                .apply { setReadTimeout(properties.readTimeout) },
        )
        .build()

    override fun approve(request: PgApprovalRequest): PgApprovalResult {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "PG 호출은 DB 트랜잭션 밖에서 해야 합니다 (ADR-0006)"
        }

        val response = try {
            restClient.post()
                .uri("/v1/payments")
                .body(PgApprovalBody(request.idempotencyKey, request.orderId.toString(), request.amount.amount))
                .retrieve()
                .body<PgApprovalResponse>()
        } catch (e: RestClientException) {
            // 타임아웃이나 5xx. PG 쪽에서 승인이 됐는지 알 수 없다.
            log.warn("PG 호출 실패: key={}, cause={}", request.idempotencyKey, e.message)
            return PgApprovalResult.Unavailable(e.javaClass.simpleName)
        }

        return when (response?.status) {
            "APPROVED" -> PgApprovalResult.Approved(requireNotNull(response.transactionId) { "승인 응답에 거래 번호가 없습니다" })
            "DECLINED" -> PgApprovalResult.Declined(response.reason ?: "UNKNOWN")
            else -> PgApprovalResult.Unavailable("UNEXPECTED_RESPONSE: ${response?.status}")
        }
    }

    data class PgApprovalBody(val idempotencyKey: String, val orderId: String, val amount: Long)

    data class PgApprovalResponse(val status: String, val transactionId: String? = null, val reason: String? = null)
}
