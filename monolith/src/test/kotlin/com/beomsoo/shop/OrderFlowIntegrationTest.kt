package com.beomsoo.shop

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult
import java.util.UUID

/**
 * HTTP 요청부터 MySQL까지 전 계층을 통과하는 테스트.
 * 모듈 넷(member, catalog, inventory, order)이 공개 API를 통해 실제로 협력하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class OrderFlowIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvcTester

    @Test
    fun `주문하면 재고가 예약되고, 취소하면 예약이 풀린다`() {
        val memberId = joinMember()
        val keyboard = registerProduct("키보드", 50_000, stock = 10)
        val mouse = registerProduct("마우스", 20_000, stock = 5)

        val order = post("/orders", orderJson(memberId, keyboard to 2, mouse to 1))
        assertThat(order).hasStatus(HttpStatus.CREATED)
        val orderId = order.json<String>("$.id")

        val detail = mvc.get().uri("/orders/$orderId").exchange()
        assertThat(detail).hasStatusOk()
        assertThat(detail.json<String>("$.status")).isEqualTo("PENDING")
        assertThat(detail.json<Int>("$.totalAmount")).isEqualTo(120_000)
        assertStock(keyboard, available = 8, reserved = 2)
        assertStock(mouse, available = 4, reserved = 1)

        assertThat(post("/orders/$orderId/cancel")).hasStatus(HttpStatus.NO_CONTENT)

        assertStock(keyboard, available = 10, reserved = 0)
        assertStock(mouse, available = 5, reserved = 0)
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

    private fun orderJson(memberId: String, vararg items: Pair<String, Int>): String =
        """{"memberId": "$memberId", "items": [${items.joinToString { (id, qty) -> """{"productId": "$id", "quantity": $qty}""" }}]}"""

    private fun post(uri: String, body: String? = null): MvcTestResult =
        mvc.post().uri(uri).apply { if (body != null) contentType(MediaType.APPLICATION_JSON).content(body) }.exchange()

    private fun <T> MvcTestResult.json(path: String): T = JsonPath.read(response.contentAsString, path)
}
