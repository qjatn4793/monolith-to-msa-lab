package com.beomsoo.shop.member.infrastructure.persistence

import com.beomsoo.shop.member.domain.Email
import com.beomsoo.shop.member.domain.Member
import com.beomsoo.shop.member.domain.MemberId
import com.beomsoo.shop.member.domain.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * 도메인 저장소(MemberRepository)의 JPA 구현.
 *
 * save: 이미 있는 회원이면 영속성 컨텍스트에 올라와 있는 엔티티에 도메인 상태를 옮겨 담는다.
 * 같은 트랜잭션에서 findById로 불러온 엔티티라 추가 조회가 없고, 커밋 시점에 변경 감지로 UPDATE 된다.
 * 엔티티의 version도 그대로 유지되므로 낙관적 락이 동작한다.
 */
@Component
class MemberPersistenceAdapter(
    private val jpaRepository: MemberJpaRepository,
) : MemberRepository {

    override fun save(member: Member) {
        val entity = jpaRepository.findByIdOrNull(member.id.value)
        if (entity == null) {
            jpaRepository.save(MemberJpaEntity.from(member))
        } else {
            entity.update(member)
        }
    }

    override fun findById(id: MemberId): Member? = jpaRepository.findByIdOrNull(id.value)?.toDomain()

    override fun existsByEmail(email: Email): Boolean = jpaRepository.existsByEmail(email.value)
}
