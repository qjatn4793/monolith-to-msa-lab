package com.beomsoo.fakepg

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FakePgControllerTest {

    private val controller = FakePgController(FakePgBehavior())

    @Test
    fun `한도 이하 금액은 승인하고 한도를 넘으면 거절한다`() {
        val approved = controller.approve(ApprovalRequest("key-1", "order-1", 1_000_000)).body!!
        val declined = controller.approve(ApprovalRequest("key-2", "order-2", 1_000_001)).body!!

        assertEquals("APPROVED", approved.status)
        assertNotNull(approved.transactionId)
        assertEquals("DECLINED", declined.status)
        assertEquals("LIMIT_EXCEEDED", declined.reason)
    }

    @Test
    fun `같은 멱등성 키로 다시 요청하면 같은 결과를 돌려준다`() {
        val first = controller.approve(ApprovalRequest("same-key", "order-1", 1000)).body!!
        val second = controller.approve(ApprovalRequest("same-key", "order-1", 1000)).body!!

        assertEquals(first, second)
    }
}
