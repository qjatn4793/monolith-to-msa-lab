package com.beomsoo.shop.member.domain

import com.beomsoo.shop.shared.domain.InvalidInputException
import java.time.Instant

/**
 * 회원 애그리거트 루트.
 *
 * 순수 Kotlin 클래스다. JPA 어노테이션이 없고, 영속성은 infrastructure/persistence가 따로 맡는다 (ADR-0004).
 * 상태는 메서드를 통해서만 바뀌고(private set), 바뀔 때마다 규칙을 검사한다.
 */
class Member(
    val id: MemberId,
    val email: Email,
    name: String,
    status: MemberStatus,
    val joinedAt: Instant,
) {
    var name: String = validateName(name)
        private set

    var status: MemberStatus = status
        private set

    val isActive: Boolean get() = status == MemberStatus.ACTIVE

    fun changeName(newName: String) {
        ensureActive()
        name = validateName(newName)
    }

    fun withdraw() {
        ensureActive()
        status = MemberStatus.WITHDRAWN
    }

    private fun ensureActive() {
        if (!isActive) throw MemberAlreadyWithdrawnException(id)
    }

    companion object {
        private const val MAX_NAME_LENGTH = 50

        /** 새 회원을 만든다. 저장된 회원을 복원할 때는 생성자를 쓴다. */
        fun join(email: Email, name: String, now: Instant): Member =
            Member(MemberId.new(), email, name, MemberStatus.ACTIVE, now)

        private fun validateName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
                throw InvalidInputException("이름은 1자 이상 ${MAX_NAME_LENGTH}자 이하여야 합니다.")
            }
            return trimmed
        }
    }
}

enum class MemberStatus {
    ACTIVE,
    WITHDRAWN,
}
