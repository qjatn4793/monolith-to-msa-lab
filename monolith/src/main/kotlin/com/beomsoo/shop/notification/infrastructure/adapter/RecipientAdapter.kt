package com.beomsoo.shop.notification.infrastructure.adapter

import com.beomsoo.shop.member.api.MemberFacade
import com.beomsoo.shop.notification.application.port.out.LoadRecipientPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 아웃바운드 어댑터: notification → member.
 *
 * 이름을 MemberAdapter로 지었다가 order 모듈의 MemberAdapter와 Spring 빈 이름(memberAdapter)이 충돌했다.
 * 패키지가 달라도 빈 이름은 컨텍스트 전체에서 하나다. 그래서 자기 모듈의 포트(LoadRecipientPort) 기준으로 이름을 지었다.
 */
@Component
class RecipientAdapter(
    private val memberFacade: MemberFacade,
) : LoadRecipientPort {

    override fun loadEmail(memberId: UUID): String? = memberFacade.findMember(memberId)?.email
}
