package com.beomsoo.shop.member.infrastructure.web

import com.beomsoo.shop.member.domain.Member
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

// 형식 검증(빈 값 등)은 여기서, 비즈니스 규칙 검증(이메일 형식, 이름 길이)은 도메인에서 한다.

data class JoinMemberRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val name: String,
)

data class ChangeMemberNameRequest(
    @field:NotBlank val name: String,
)

data class MemberResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val status: String,
    val joinedAt: Instant,
) {
    companion object {
        fun from(member: Member) = MemberResponse(
            id = member.id.value,
            email = member.email.value,
            name = member.name,
            status = member.status.name,
            joinedAt = member.joinedAt,
        )
    }
}
