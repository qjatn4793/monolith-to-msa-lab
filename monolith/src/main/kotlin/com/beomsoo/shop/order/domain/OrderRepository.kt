package com.beomsoo.shop.order.domain

interface OrderRepository {

    fun save(order: Order)

    fun findById(id: OrderId): Order?
}
