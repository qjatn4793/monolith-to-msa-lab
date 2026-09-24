package com.beomsoo.shop.order.application.port.out

import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import java.util.UUID

interface OrderListPort {

    fun findByMemberId(memberId: UUID, query: PageQuery): PageResult<Order>
}
