package com.beomsoo.shop.member.domain

import com.beomsoo.shop.shared.domain.newId
import java.util.UUID

@JvmInline
value class MemberId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): MemberId = MemberId(newId())
    }
}
