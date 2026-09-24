package com.beomsoo.shop.member.api

import java.util.UUID

/**
 * member 모듈이 다른 모듈에 공개하는 동기 API.
 *
 * 다른 모듈은 이 인터페이스와 DTO만 알 수 있다 (@NamedInterface("api")).
 * 도메인 객체(Member, MemberId)를 내보내지 않고, UUID와 기본 타입만 쓴다.
 * MSA로 전환하면 이 인터페이스가 그대로 HTTP API 계약이 된다.
 */
interface MemberFacade {

    fun findMember(memberId: UUID): MemberInfo?
}

data class MemberInfo(
    val id: UUID,
    val email: String,
    val name: String,
    val active: Boolean,
)
