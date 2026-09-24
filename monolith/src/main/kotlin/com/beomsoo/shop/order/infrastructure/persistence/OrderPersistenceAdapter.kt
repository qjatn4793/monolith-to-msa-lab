package com.beomsoo.shop.order.infrastructure.persistence

import com.beomsoo.shop.order.application.port.out.OrderListPort
import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.order.domain.OrderRepository
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OrderPersistenceAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository, OrderListPort {

    override fun save(order: Order) {
        val entity = jpaRepository.findByIdOrNull(order.id.value)
        if (entity == null) {
            jpaRepository.save(OrderJpaEntity.from(order))
        } else {
            entity.update(order)
        }
    }

    override fun findById(id: OrderId): Order? = jpaRepository.findByIdOrNull(id.value)?.toDomain()

    override fun findByMemberId(memberId: UUID, query: PageQuery): PageResult<Order> {
        val page = jpaRepository.findByMemberId(memberId, PageRequest.of(query.page, query.size, Sort.by(Sort.Direction.DESC, "id")))
        return PageResult(page.content.map { it.toDomain() }, query.page, query.size, page.totalElements)
    }
}
