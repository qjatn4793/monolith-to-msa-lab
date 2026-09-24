package com.beomsoo.shop.catalog.infrastructure.persistence

import com.beomsoo.shop.catalog.domain.Product
import com.beomsoo.shop.catalog.domain.ProductId
import com.beomsoo.shop.catalog.domain.ProductStatus
import com.beomsoo.shop.shared.domain.Money
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "product")
class ProductJpaEntity(
    @Id
    val id: UUID,
    var name: String,
    var price: Long,
    var description: String?,
    @Enumerated(EnumType.STRING)
    var status: ProductStatus,
    val registeredAt: Instant,
) {
    @Version
    var version: Long? = null

    fun update(product: Product) {
        name = product.name
        price = product.price.amount
        description = product.description
        status = product.status
    }

    fun toDomain(): Product = Product(
        id = ProductId(id),
        name = name,
        price = Money(price),
        description = description,
        status = status,
        registeredAt = registeredAt,
    )

    companion object {
        fun from(product: Product) = ProductJpaEntity(
            id = product.id.value,
            name = product.name,
            price = product.price.amount,
            description = product.description,
            status = product.status,
            registeredAt = product.registeredAt,
        )
    }
}
