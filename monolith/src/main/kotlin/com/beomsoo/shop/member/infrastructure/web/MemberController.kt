package com.beomsoo.shop.member.infrastructure.web

import com.beomsoo.shop.member.application.port.`in`.ChangeMemberUseCase
import com.beomsoo.shop.member.application.port.`in`.GetMemberQuery
import com.beomsoo.shop.member.application.port.`in`.JoinMemberCommand
import com.beomsoo.shop.member.application.port.`in`.JoinMemberUseCase
import com.beomsoo.shop.member.domain.MemberId
import com.beomsoo.shop.shared.infrastructure.web.IdResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/**
 * 인바운드 어댑터. HTTP 요청을 유스케이스 호출로 바꾸고, 결과를 응답 DTO로 바꾼다.
 * 비즈니스 판단은 하지 않는다.
 */
@RestController
@RequestMapping("/members")
class MemberController(
    private val joinMemberUseCase: JoinMemberUseCase,
    private val changeMemberUseCase: ChangeMemberUseCase,
    private val getMemberQuery: GetMemberQuery,
) {

    @PostMapping
    fun join(@Valid @RequestBody request: JoinMemberRequest): ResponseEntity<IdResponse> {
        val id = joinMemberUseCase.join(JoinMemberCommand(email = request.email, name = request.name))
        return ResponseEntity.created(URI.create("/members/$id")).body(IdResponse(id.value))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): MemberResponse = MemberResponse.from(getMemberQuery.getMember(MemberId(id)))

    @PatchMapping("/{id}")
    fun changeName(@PathVariable id: UUID, @Valid @RequestBody request: ChangeMemberNameRequest): ResponseEntity<Unit> {
        changeMemberUseCase.changeName(MemberId(id), request.name)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/withdraw")
    fun withdraw(@PathVariable id: UUID): ResponseEntity<Unit> {
        changeMemberUseCase.withdraw(MemberId(id))
        return ResponseEntity.noContent().build()
    }
}
