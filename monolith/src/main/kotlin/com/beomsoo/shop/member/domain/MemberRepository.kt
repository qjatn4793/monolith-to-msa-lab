package com.beomsoo.shop.member.domain

/**
 * 회원 저장소. 도메인이 필요로 하는 연산만 도메인의 언어로 정의한다.
 * 구현(JPA)은 infrastructure/persistence/MemberPersistenceAdapter에 있다.
 */
interface MemberRepository {

    fun save(member: Member)

    fun findById(id: MemberId): Member?

    fun existsByEmail(email: Email): Boolean
}
