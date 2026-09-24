package com.beomsoo.shop.catalog.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import com.beomsoo.shop.shared.domain.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProductTest {

    private val now = Instant.parse("2026-09-24T00:00:00Z")

    private fun product() = Product.register("키보드", Money(50_000), "무선 키보드", now)

    @Test
    fun `등록하면 판매 중이고 빈 설명은 null로 저장한다`() {
        assertEquals(ProductStatus.ON_SALE, product().status)
        assertNull(Product.register("키보드", Money(50_000), "   ", now).description)
    }

    @Test
    fun `가격은 0원일 수 없다`() {
        assertThrows<InvalidInputException> { Product.register("키보드", Money.ZERO, null, now) }
        assertThrows<InvalidInputException> { product().changePrice(Money.ZERO) }
    }

    @Test
    fun `판매 중지와 재개는 상태가 맞을 때만 할 수 있다`() {
        val product = product()
        product.stopSelling()

        assertFalse(product.isOnSale)
        assertThrows<ProductAlreadyStoppedException> { product.stopSelling() }

        product.resumeSelling()
        assertThrows<ProductAlreadyOnSaleException> { product.resumeSelling() }
    }
}
