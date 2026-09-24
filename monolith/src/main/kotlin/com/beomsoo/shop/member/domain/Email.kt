package com.beomsoo.shop.member.domain

import com.beomsoo.shop.shared.domain.InvalidInputException

/**
 * 이메일 값 객체. 생성되는 순간 형식이 검증되고, 대소문자를 구분하지 않도록 소문자로 정규화한다.
 * 이 타입의 값이 존재한다면 항상 올바른 이메일이다.
 */
@JvmInline
value class Email private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private const val MAX_LENGTH = 255
        private val PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (normalized.length > MAX_LENGTH || !PATTERN.matches(normalized)) {
                throw InvalidInputException("올바른 이메일 형식이 아닙니다: $raw")
            }
            return Email(normalized)
        }
    }
}
