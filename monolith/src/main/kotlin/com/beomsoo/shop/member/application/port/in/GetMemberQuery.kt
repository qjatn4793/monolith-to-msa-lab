package com.beomsoo.shop.member.application.port.`in`

import com.beomsoo.shop.member.domain.Member
import com.beomsoo.shop.member.domain.MemberId

interface GetMemberQuery {

    /** 없으면 MemberNotFoundException */
    fun getMember(id: MemberId): Member

    fun findMember(id: MemberId): Member?
}
