package com.beomsoo.shop.inventory.infrastructure.persistence

import com.beomsoo.shop.inventory.domain.Stock
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

@Entity
@Table(name = "stock")
class StockJpaEntity(
    @Id
    val productId: UUID,
    var available: Int,
    var reserved: Int,
) {
    /** 같은 상품의 재고를 동시에 바꾸면 한쪽은 OptimisticLockingFailureException으로 실패한다. */
    @Version
    var version: Long? = null

    fun update(stock: Stock) {
        available = stock.available
        reserved = stock.reserved
    }

    fun toDomain(): Stock = Stock(productId, available, reserved)

    companion object {
        fun from(stock: Stock) = StockJpaEntity(stock.productId, stock.available, stock.reserved)
    }
}
