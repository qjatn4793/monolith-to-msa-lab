package com.beomsoo.shop.member.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberJpaRepository : JpaRepository<MemberJpaEntity, UUID> {

    fun existsByEmail(email: String): Boolean
}
