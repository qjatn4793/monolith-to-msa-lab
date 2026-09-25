package com.beomsoo.shop.payment.infrastructure

import com.beomsoo.shop.payment.application.port.out.PgApprovalRequest
import com.beomsoo.shop.payment.application.port.out.PgApprovalResult
import com.beomsoo.shop.payment.infrastructure.adapter.PgClientAdapter
import com.beomsoo.shop.payment.infrastructure.adapter.PgProperties
import com.beomsoo.shop.shared.domain.Money
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * PG 클라이언트가 PG의 여러 응답을 올바른 결과로 바꾸는지 확인한다.
 * JDK에 들어 있는 HttpServer로 가짜 PG를 띄운다.
 */
class PgClientAdapterTest {

    private var status = 200
    private var body = ""
    private var delayMs = 0L

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/v1/payments") { exchange ->
            Thread.sleep(delayMs)
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        start()
    }

    private val adapter = PgClientAdapter(
        PgProperties(
            baseUrl = "http://localhost:${server.address.port}",
            connectTimeout = Duration.ofMillis(500),
            readTimeout = Duration.ofMillis(300),
        ),
        RestClient.builder(),
    )

    private val request = PgApprovalRequest("key-1", UUID.randomUUID(), Money(10_000))

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `승인 응답은 Approved가 된다`() {
        body = """{"status": "APPROVED", "transactionId": "pg-123"}"""

        assertEquals(PgApprovalResult.Approved("pg-123"), adapter.approve(request))
    }

    @Test
    fun `거절 응답은 Declined가 된다`() {
        body = """{"status": "DECLINED", "reason": "LIMIT_EXCEEDED"}"""

        assertEquals(PgApprovalResult.Declined("LIMIT_EXCEEDED"), adapter.approve(request))
    }

    @Test
    fun `5xx 응답은 결과를 알 수 없으므로 Unavailable이 된다`() {
        status = 500

        assertIs<PgApprovalResult.Unavailable>(adapter.approve(request))
    }

    @Test
    fun `응답이 읽기 타임아웃보다 늦으면 Unavailable이 된다`() {
        delayMs = 1000
        body = """{"status": "APPROVED", "transactionId": "pg-late"}"""

        val result = adapter.approve(request)

        // PG 쪽에서는 승인됐을 수도 있다. 이 결과를 "실패"로 처리하는 것의 위험은 ADR-0012에 적어두었다.
        assertIs<PgApprovalResult.Unavailable>(result)
    }
}
