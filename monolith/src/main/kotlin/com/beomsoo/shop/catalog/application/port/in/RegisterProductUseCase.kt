package com.beomsoo.shop.catalog.application.port.`in`

import com.beomsoo.shop.catalog.domain.ProductId

interface RegisterProductUseCase {

    fun register(command: RegisterProductCommand): ProductId
}

data class RegisterProductCommand(
    val name: String,
    val price: Long,
    val description: String?,
)
