package com.beomsoo.shop.catalog.domain

import com.beomsoo.shop.shared.domain.newId
import java.util.UUID

@JvmInline
value class ProductId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): ProductId = ProductId(newId())
    }
}
