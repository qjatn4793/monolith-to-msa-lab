package com.beomsoo.shop.order.infrastructure.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, UUID> {

    fun findByMemberId(memberId: UUID, pageable: Pageable): Page<OrderJpaEntity>
}
