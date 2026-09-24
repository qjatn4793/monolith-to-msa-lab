package com.beomsoo.shop.catalog.infrastructure.web

import com.beomsoo.shop.catalog.domain.Product
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.util.UUID

data class RegisterProductRequest(
    @field:NotBlank val name: String,
    @field:Positive val price: Long,
    val description: String? = null,
)

data class ChangePriceRequest(
    @field:Positive val price: Long,
)

data class ProductResponse(
    val id: UUID,
    val name: String,
    val price: Long,
    val description: String?,
    val status: String,
    val registeredAt: Instant,
) {
    companion object {
        fun from(product: Product) = ProductResponse(
            id = product.id.value,
            name = product.name,
            price = product.price.amount,
            description = product.description,
            status = product.status.name,
            registeredAt = product.registeredAt,
        )
    }
}
