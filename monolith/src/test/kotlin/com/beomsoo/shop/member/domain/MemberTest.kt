package com.beomsoo.shop.member.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 도메인 테스트는 Spring도 DB도 없이 돈다. 도메인이 순수 Kotlin이라 가능하다 (ADR-0004). */
class MemberTest {

    private val now = Instant.parse("2026-09-24T00:00:00Z")

    @Test
    fun `이메일은 소문자로 정규화되고 형식이 틀리면 만들 수 없다`() {
        assertEquals("user@example.com", Email.of("  User@Example.COM ").value)
        assertThrows<InvalidInputException> { Email.of("not-an-email") }
    }

    @Test
    fun `가입하면 활성 회원이다`() {
        val member = Member.join(Email.of("a@b.com"), " 홍길동 ", now)

        assertEquals(MemberStatus.ACTIVE, member.status)
        assertEquals("홍길동", member.name)
    }

    @Test
    fun `이름은 비어 있거나 50자를 넘을 수 없다`() {
        assertThrows<InvalidInputException> { Member.join(Email.of("a@b.com"), "  ", now) }
        assertThrows<InvalidInputException> { Member.join(Email.of("a@b.com"), "가".repeat(51), now) }
    }

    @Test
    fun `탈퇴한 회원은 다시 탈퇴하거나 이름을 바꿀 수 없다`() {
        val member = Member.join(Email.of("a@b.com"), "홍길동", now)
        member.withdraw()

        assertFalse(member.isActive)
        assertThrows<MemberAlreadyWithdrawnException> { member.withdraw() }
        assertThrows<MemberAlreadyWithdrawnException> { member.changeName("임꺽정") }
    }
}
