package com.beomsoo.shop.inventory.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StockJpaRepository : JpaRepository<StockJpaEntity, UUID>
