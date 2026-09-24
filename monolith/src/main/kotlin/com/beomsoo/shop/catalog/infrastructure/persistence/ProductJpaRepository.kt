package com.beomsoo.shop.catalog.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProductJpaRepository : JpaRepository<ProductJpaEntity, UUID>
