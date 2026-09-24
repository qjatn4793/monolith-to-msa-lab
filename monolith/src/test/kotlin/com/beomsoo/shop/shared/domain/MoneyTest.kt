package com.beomsoo.shop.shared.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MoneyTest {

    @Test
    fun `음수 금액은 만들 수 없다`() {
        assertThrows<InvalidInputException> { Money(-1) }
    }

    @Test
    fun `더하고 곱하고 합산한다`() {
        assertEquals(Money(3000), Money(1000) + Money(2000))
        assertEquals(Money(3000), Money(1000) * 3)
        assertEquals(Money(6000), listOf(Money(1000), Money(2000), Money(3000)).sum())
    }

    @Test
    fun `오버플로우는 조용히 넘어가지 않는다`() {
        assertThrows<ArithmeticException> { Money(Long.MAX_VALUE) * 2 }
    }
}
