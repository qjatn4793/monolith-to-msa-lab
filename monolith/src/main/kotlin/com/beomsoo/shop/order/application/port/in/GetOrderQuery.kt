package com.beomsoo.shop.order.application.port.`in`

import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import java.util.UUID

interface GetOrderQuery {

    fun getOrder(id: OrderId): Order

    fun getOrdersOfMember(memberId: UUID, query: PageQuery): PageResult<Order>
}
