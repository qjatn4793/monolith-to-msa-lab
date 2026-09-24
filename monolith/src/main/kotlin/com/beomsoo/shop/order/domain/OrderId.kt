package com.beomsoo.shop.order.domain

import com.beomsoo.shop.shared.domain.newId
import java.util.UUID

@JvmInline
value class OrderId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): OrderId = OrderId(newId())
    }
}
