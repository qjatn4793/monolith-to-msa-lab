package com.beomsoo.shop.member.infrastructure.persistence

import com.beomsoo.shop.member.domain.Email
import com.beomsoo.shop.member.domain.Member
import com.beomsoo.shop.member.domain.MemberId
import com.beomsoo.shop.member.domain.MemberStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * member 테이블과 1:1로 대응하는 JPA 엔티티. 도메인 모델(Member)과 별개다 (ADR-0004).
 * 도메인 규칙은 여기에 두지 않는다. 변환(from, update, toDomain)만 한다.
 */
@Entity
@Table(name = "member")
class MemberJpaEntity(
    @Id
    val id: UUID,
    val email: String,
    var name: String,
    @Enumerated(EnumType.STRING)
    var status: MemberStatus,
    val joinedAt: Instant,
) {
    /** 낙관적 락. null이면 Spring Data가 새 엔티티로 판단해 INSERT 한다. */
    @Version
    var version: Long? = null

    fun update(member: Member) {
        name = member.name
        status = member.status
    }

    fun toDomain(): Member = Member(
        id = MemberId(id),
        email = Email.of(email),
        name = name,
        status = status,
        joinedAt = joinedAt,
    )

    companion object {
        fun from(member: Member) = MemberJpaEntity(
            id = member.id.value,
            email = member.email.value,
            name = member.name,
            status = member.status,
            joinedAt = member.joinedAt,
        )
    }
}
