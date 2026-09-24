package com.beomsoo.shop.member.application.port.`in`

import com.beomsoo.shop.member.domain.MemberId

interface ChangeMemberUseCase {

    fun changeName(id: MemberId, newName: String)

    fun withdraw(id: MemberId)
}
