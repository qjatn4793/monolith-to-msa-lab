package com.beomsoo.shop.order.application.service

import com.beomsoo.shop.order.application.port.`in`.GetOrderQuery
import com.beomsoo.shop.order.application.port.out.OrderListPort
import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.order.domain.OrderNotFoundException
import com.beomsoo.shop.order.domain.OrderRepository
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrderQueryService(
    private val orderRepository: OrderRepository,
    private val orderListPort: OrderListPort,
) : GetOrderQuery {

    override fun getOrder(id: OrderId): Order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)

    override fun getOrdersOfMember(memberId: UUID, query: PageQuery): PageResult<Order> =
        orderListPort.findByMemberId(memberId, query)
}
