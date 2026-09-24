package com.beomsoo.shop.member.application.service

import com.beomsoo.shop.member.application.port.`in`.ChangeMemberUseCase
import com.beomsoo.shop.member.application.port.`in`.JoinMemberCommand
import com.beomsoo.shop.member.application.port.`in`.JoinMemberUseCase
import com.beomsoo.shop.member.domain.DuplicateEmailException
import com.beomsoo.shop.member.domain.Email
import com.beomsoo.shop.member.domain.Member
import com.beomsoo.shop.member.domain.MemberId
import com.beomsoo.shop.member.domain.MemberNotFoundException
import com.beomsoo.shop.member.domain.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 유스케이스 구현. 트랜잭션 경계가 여기다.
 *
 * 서비스는 흐름만 조율한다: 불러오고 → 도메인에게 시키고 → 저장한다.
 * "이름은 50자 이하", "탈퇴한 회원은 수정 불가" 같은 규칙은 도메인(Member)이 가지고 있다.
 */
@Service
@Transactional
class MemberCommandService(
    private val memberRepository: MemberRepository,
    private val clock: Clock,
) : JoinMemberUseCase, ChangeMemberUseCase {

    override fun join(command: JoinMemberCommand): MemberId {
        val email = Email.of(command.email)
        // 동시에 같은 이메일로 가입하면 이 검사를 둘 다 통과할 수 있다.
        // 그 경우는 DB 유니크 제약이 막고, GlobalExceptionHandler가 409로 응답한다.
        if (memberRepository.existsByEmail(email)) throw DuplicateEmailException(email)

        val member = Member.join(email, command.name, clock.instant())
        memberRepository.save(member)
        return member.id
    }

    override fun changeName(id: MemberId, newName: String) {
        val member = load(id)
        member.changeName(newName)
        memberRepository.save(member)
    }

    override fun withdraw(id: MemberId) {
        val member = load(id)
        member.withdraw()
        memberRepository.save(member)
    }

    private fun load(id: MemberId): Member = memberRepository.findById(id) ?: throw MemberNotFoundException(id)
}
