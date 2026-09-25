package com.beomsoo.shop

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult
import java.time.Duration
import java.util.UUID

/**
 * HTTP 요청부터 MySQL까지 전 계층을 통과하는 테스트.
 * 모듈 여섯 개가 공개 API와 이벤트로 실제로 협력하는지 확인한다. PG와 알림 발송만 가짜다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, FakeExternalSystemsConfiguration::class)
class OrderFlowIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvcTester

    @Test
    fun `결제가 승인되면 주문이 확정되고, 재고가 차감되고, 확정 알림이 간다`() {
        val memberId = joinMember()
        val keyboard = registerProduct("키보드", 50_000, stock = 10)
        val mouse = registerProduct("마우스", 20_000, stock = 5)

        val order = post("/orders", orderJson(memberId, keyboard to 2, mouse to 1))
        assertThat(order).hasStatus(HttpStatus.CREATED)
        assertThat(order.json<String>("$.status")).isEqualTo("CONFIRMED")
        val orderId = order.json<String>("$.id")

        val detail = mvc.get().uri("/orders/$orderId").exchange()
        assertThat(detail.json<String>("$.status")).isEqualTo("CONFIRMED")
        assertThat(detail.json<Int>("$.totalAmount")).isEqualTo(120_000)
        assertStock(keyboard, available = 8, reserved = 0)
        assertStock(mouse, available = 4, reserved = 0)

        val payments = mvc.get().uri("/payments?orderId=$orderId").exchange()
        assertThat(payments.json<List<String>>("$[*].status")).containsExactly("APPROVED")

        // 알림은 주문 확정 트랜잭션이 커밋된 뒤 다른 스레드에서 비동기로 간다.
        awaitNotifications(memberId, "ORDER_CONFIRMED")
    }

    @Test
    fun `결제가 거절되면 주문이 취소되고, 재고 예약이 풀리고, 취소 알림이 간다`() {
        val memberId = joinMember()
        val tv = registerProduct("TV", 600_000, stock = 5)

        // 120만 원: 가짜 PG의 한도(100만 원)를 넘어서 거절된다
        val order = post("/orders", orderJson(memberId, tv to 2))
        assertThat(order).hasStatus(HttpStatus.CREATED)
        assertThat(order.json<String>("$.status")).isEqualTo("CANCELLED")
        val orderId = order.json<String>("$.id")

        val detail = mvc.get().uri("/orders/$orderId").exchange()
        assertThat(detail.json<String>("$.cancelReason")).isEqualTo("PAYMENT_FAILED")
        assertStock(tv, available = 5, reserved = 0)

        val payments = mvc.get().uri("/payments?orderId=$orderId").exchange()
        assertThat(payments.json<List<String>>("$[*].status")).containsExactly("FAILED")
        assertThat(payments.json<List<String>>("$[*].failureReason")).containsExactly("DECLINED: LIMIT_EXCEEDED")

        awaitNotifications(memberId, "ORDER_CANCELLED")
    }

    @Test
    fun `재고가 하나라도 부족하면 주문이 거절되고 어떤 재고도 예약되지 않는다`() {
        val memberId = joinMember()
        val enough = registerProduct("충분한 상품", 1_000, stock = 10)
        val short = registerProduct("부족한 상품", 1_000, stock = 1)

        val result = post("/orders", orderJson(memberId, enough to 3, short to 2))

        assertThat(result).hasStatus(HttpStatus.CONFLICT)
        assertThat(result.json<String>("$.code")).isEqualTo("OUT_OF_STOCK")
        assertStock(enough, available = 10, reserved = 0)
        assertStock(short, available = 1, reserved = 0)
    }

    @Test
    fun `상품 가격이 바뀌어도 이미 한 주문의 금액은 그대로다`() {
        val memberId = joinMember()
        val product = registerProduct("모니터", 300_000, stock = 3)
        val orderId = post("/orders", orderJson(memberId, product to 1)).json<String>("$.id")

        val change = mvc.patch().uri("/products/$product/price")
            .contentType(MediaType.APPLICATION_JSON).content("""{"price": 250000}""").exchange()
        assertThat(change).hasStatus(HttpStatus.NO_CONTENT)

        val detail = mvc.get().uri("/orders/$orderId").exchange()
        assertThat(detail.json<Int>("$.lines[0].unitPrice")).isEqualTo(300_000)
    }

    @Test
    fun `판매 중지된 상품과 탈퇴한 회원은 주문할 수 없다`() {
        val memberId = joinMember()
        val product = registerProduct("단종 상품", 1_000, stock = 10)
        post("/products/$product/stop-selling")

        val stopped = post("/orders", orderJson(memberId, product to 1))
        assertThat(stopped).hasStatus(HttpStatus.CONFLICT)
        assertThat(stopped.json<String>("$.code")).isEqualTo("PRODUCT_NOT_ORDERABLE")

        val onSale = registerProduct("판매 상품", 1_000, stock = 10)
        post("/members/$memberId/withdraw")
        val withdrawn = post("/orders", orderJson(memberId, onSale to 1))
        assertThat(withdrawn).hasStatus(HttpStatus.CONFLICT)
        assertThat(withdrawn.json<String>("$.code")).isEqualTo("ORDERER_INACTIVE")
    }

    @Test
    fun `에러 응답은 ProblemDetail 형식이다`() {
        val duplicated = "dup-${UUID.randomUUID()}@example.com"
        post("/members", """{"email": "$duplicated", "name": "홍길동"}""")

        val conflict = post("/members", """{"email": "$duplicated", "name": "홍길동"}""")
        assertThat(conflict).hasStatus(HttpStatus.CONFLICT)
        assertThat(conflict.json<String>("$.code")).isEqualTo("DUPLICATE_EMAIL")
        assertThat(conflict.json<Int>("$.status")).isEqualTo(409)

        val invalid = post("/orders", """{"memberId": "${UUID.randomUUID()}", "items": []}""")
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST)
        assertThat(invalid.response.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    }

    @Test
    fun `회원의 주문 목록을 최신순으로 조회한다`() {
        val memberId = joinMember()
        val product = registerProduct("상품", 1_000, stock = 10)
        val first = post("/orders", orderJson(memberId, product to 1)).json<String>("$.id")
        val second = post("/orders", orderJson(memberId, product to 2)).json<String>("$.id")

        val page = mvc.get().uri("/orders?memberId=$memberId&page=0&size=10").exchange()

        assertThat(page).hasStatusOk()
        assertThat(page.json<List<String>>("$.content[*].id")).containsExactly(second, first)
        assertThat(page.json<Int>("$.totalElements")).isEqualTo(2)
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun joinMember(): String =
        post("/members", """{"email": "user-${UUID.randomUUID()}@example.com", "name": "홍길동"}""").json("$.id")

    private fun registerProduct(name: String, price: Long, stock: Int): String {
        val productId = post("/products", """{"name": "$name", "price": $price}""").json<String>("$.id")
        assertThat(post("/stocks/$productId/receive", """{"quantity": $stock}""")).hasStatus(HttpStatus.NO_CONTENT)
        return productId
    }

    private fun assertStock(productId: String, available: Int, reserved: Int) {
        val stock = mvc.get().uri("/stocks/$productId").exchange()
        assertThat(stock.json<Int>("$.available")).isEqualTo(available)
        assertThat(stock.json<Int>("$.reserved")).isEqualTo(reserved)
    }

    private fun awaitNotifications(memberId: String, vararg types: String) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val notifications = mvc.get().uri("/notifications?memberId=$memberId").exchange()
            assertThat(notifications.json<List<String>>("$.content[*].type")).containsExactly(*types)
        }
    }

    private fun orderJson(memberId: String, vararg items: Pair<String, Int>): String =
        """{"memberId": "$memberId", "items": [${items.joinToString { (id, qty) -> """{"productId": "$id", "quantity": $qty}""" }}]}"""

    private fun post(uri: String, body: String? = null): MvcTestResult =
        mvc.post().uri(uri).apply { if (body != null) contentType(MediaType.APPLICATION_JSON).content(body) }.exchange()

    private fun <T> MvcTestResult.json(path: String): T = JsonPath.read(response.contentAsString, path)
}
