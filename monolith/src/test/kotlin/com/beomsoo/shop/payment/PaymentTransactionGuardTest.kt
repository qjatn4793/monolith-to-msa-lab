package com.beomsoo.shop.payment

import com.beomsoo.shop.TestcontainersConfiguration
import com.beomsoo.shop.payment.api.PaymentFacade
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertContains

/**
 * ADR-0006을 실행 시점에 강제하는지 확인한다.
 * 가짜 PG가 아니라 실제 PgClientAdapter를 쓴다. HTTP 요청을 보내기 전에 막히므로 PG 서버는 필요 없다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PaymentTransactionGuardTest {

    @Autowired
    lateinit var paymentFacade: PaymentFacade

    @Autowired
    lateinit var tx: TransactionTemplate

    @Test
    fun `DB 트랜잭션 안에서 결제를 요청하면 PG를 호출하기 전에 거부된다`() {
        val e = assertThrows<IllegalStateException> {
            tx.executeWithoutResult { paymentFacade.pay(UUID.randomUUID(), 10_000) }
        }
        assertContains(e.message!!, "PG 호출은 DB 트랜잭션 밖에서")
    }
}
