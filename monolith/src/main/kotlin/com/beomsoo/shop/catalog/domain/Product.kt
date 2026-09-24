package com.beomsoo.shop.catalog.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import com.beomsoo.shop.shared.domain.Money
import java.time.Instant

/**
 * 상품 애그리거트 루트. 판매 정보(이름, 가격, 설명, 판매 상태)만 가진다.
 * 재고 수량은 inventory 모듈이 따로 관리한다 (ADR-0002).
 */
class Product(
    val id: ProductId,
    name: String,
    price: Money,
    description: String?,
    status: ProductStatus,
    val registeredAt: Instant,
) {
    var name: String = validateName(name)
        private set

    var price: Money = validatePrice(price)
        private set

    var description: String? = validateDescription(description)
        private set

    var status: ProductStatus = status
        private set

    val isOnSale: Boolean get() = status == ProductStatus.ON_SALE

    fun changePrice(newPrice: Money) {
        price = validatePrice(newPrice)
    }

    fun stopSelling() {
        if (!isOnSale) throw ProductAlreadyStoppedException(id)
        status = ProductStatus.STOPPED
    }

    fun resumeSelling() {
        if (isOnSale) throw ProductAlreadyOnSaleException(id)
        status = ProductStatus.ON_SALE
    }

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_DESCRIPTION_LENGTH = 1000

        fun register(name: String, price: Money, description: String?, now: Instant): Product =
            Product(ProductId.new(), name, price, description, ProductStatus.ON_SALE, now)

        private fun validateName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
                throw InvalidInputException("상품명은 1자 이상 ${MAX_NAME_LENGTH}자 이하여야 합니다.")
            }
            return trimmed
        }

        private fun validatePrice(price: Money): Money {
            if (price == Money.ZERO) throw InvalidInputException("상품 가격은 0원일 수 없습니다.")
            return price
        }

        private fun validateDescription(description: String?): String? {
            val trimmed = description?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmed != null && trimmed.length > MAX_DESCRIPTION_LENGTH) {
                throw InvalidInputException("상품 설명은 ${MAX_DESCRIPTION_LENGTH}자 이하여야 합니다.")
            }
            return trimmed
        }
    }
}

enum class ProductStatus {
    ON_SALE,
    STOPPED,
}
