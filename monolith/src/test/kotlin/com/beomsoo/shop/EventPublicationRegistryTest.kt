package com.beomsoo.shop

import com.beomsoo.shop.order.api.OrderConfirmedEvent
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult
import java.time.Duration
import java.util.UUID

/**
 * 이벤트 발행 저장소(Event Publication Registry)가 트랜잭셔널 아웃박스처럼 동작하는지 확인한다 (ADR-0011).
 *
 * 1. 알림 발송이 실패해도 주문은 확정된다 (모듈 사이의 장애 격리)
 * 2. 전달에 실패한 이벤트는 EVENT_PUBLICATION 테이블에 미완료로 남는다 (유실되지 않음)
 * 3. 장애가 풀린 뒤 다시 제출하면 알림이 간다 (재처리)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, FakeExternalSystemsConfiguration::class)
class EventPublicationRegistryTest {

    @Autowired
    lateinit var mvc: MockMvcTester

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sender: ControllableNotificationSender

    @Autowired
    lateinit var incompletePublications: IncompleteEventPublications

    @AfterEach
    fun recover() {
        sender.failing = false
    }

    @Test
    fun `알림이 실패해도 주문은 확정되고, 이벤트는 남아 있다가 다시 전달된다`() {
        val memberId = post("/members", """{"email": "u-${UUID.randomUUID()}@example.com", "name": "홍길동"}""").json<String>("$.id")
        val productId = post("/products", """{"name": "키보드", "price": 50000}""").json<String>("$.id")
        post("/stocks/$productId/receive", """{"quantity": 10}""")

        // 1. 알림 서버 장애 중에 주문한다
        sender.failing = true
        val order = post("/orders", """{"memberId": "$memberId", "items": [{"productId": "$productId", "quantity": 1}]}""")
        val orderId = order.json<String>("$.id")
        assertThat(order.json<String>("$.status")).isEqualTo("CONFIRMED")

        // 2. 리스너가 실패하고, 이벤트는 미완료로 남는다
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val row = publicationOf(orderId)
            assertThat(row["completion_date"]).isNull()
            assertThat(row["status"]).isEqualTo("FAILED")
        }
        println("장애 중 EVENT_PUBLICATION: ${publicationOf(orderId)}")
        assertThat(notificationTypes(memberId)).isEmpty()

        // 3. 장애가 풀린 뒤 다시 제출하면 알림이 가고, 이벤트는 완료된다
        sender.failing = false
        incompletePublications.resubmitIncompletePublications { publication ->
            (publication.event as? OrderConfirmedEvent)?.orderId?.toString() == orderId
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(notificationTypes(memberId)).containsExactly("ORDER_CONFIRMED")
            assertThat(publicationOf(orderId)["completion_date"]).isNotNull()
        }
        println("재처리 후 EVENT_PUBLICATION: ${publicationOf(orderId)}")
    }

    /**
     * 처음에는 Flyway가 소문자 event_publication을 만들고, Modulith가 대문자 EVENT_PUBLICATION을 따로 만들어 썼다.
     * 대소문자를 구분하는 MySQL에서는 두 테이블이 공존하면서 아무 에러도 나지 않았다. 다시 생기지 않도록 확인한다.
     */
    @Test
    fun `이벤트 발행 테이블은 Flyway가 만든 하나뿐이다`() {
        val tables = jdbc.queryForList("show tables", String::class.java).filter { it.equals("event_publication", ignoreCase = true) }
        val createdByFlyway = jdbc.queryForList("select script from flyway_schema_history where script like '%event_publication%'", String::class.java)

        assertThat(tables).containsExactly("EVENT_PUBLICATION")
        assertThat(createdByFlyway).containsExactly("shared/V8__create_event_publication.sql")
    }

    private fun publicationOf(orderId: String): Map<String, Any?> = jdbc.queryForMap(
        """
        select event_type, status, completion_attempts, publication_date, completion_date
        from EVENT_PUBLICATION
        where serialized_event like ?
        """.trimIndent(),
        "%$orderId%",
    )

    private fun notificationTypes(memberId: String): List<String> =
        mvc.get().uri("/notifications?memberId=$memberId").exchange().json("$.content[*].type")

    private fun post(uri: String, body: String): MvcTestResult =
        mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange()

    private fun <T> MvcTestResult.json(path: String): T = JsonPath.read(response.contentAsString, path)
}
