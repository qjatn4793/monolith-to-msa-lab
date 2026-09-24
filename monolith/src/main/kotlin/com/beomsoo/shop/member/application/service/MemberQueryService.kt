package com.beomsoo.shop.member.application.service

import com.beomsoo.shop.member.application.port.`in`.GetMemberQuery
import com.beomsoo.shop.member.domain.Member
import com.beomsoo.shop.member.domain.MemberId
import com.beomsoo.shop.member.domain.MemberNotFoundException
import com.beomsoo.shop.member.domain.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberQueryService(
    private val memberRepository: MemberRepository,
) : GetMemberQuery {

    override fun getMember(id: MemberId): Member = findMember(id) ?: throw MemberNotFoundException(id)

    override fun findMember(id: MemberId): Member? = memberRepository.findById(id)
}
