package com.beomsoo.shop.member.domain

import com.beomsoo.shop.shared.domain.BusinessException
import com.beomsoo.shop.shared.domain.ErrorType

class MemberNotFoundException(id: MemberId) :
    BusinessException(ErrorType.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다: $id")

class DuplicateEmailException(email: Email) :
    BusinessException(ErrorType.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다: $email")

class MemberAlreadyWithdrawnException(id: MemberId) :
    BusinessException(ErrorType.CONFLICT, "MEMBER_WITHDRAWN", "탈퇴한 회원입니다: $id")
