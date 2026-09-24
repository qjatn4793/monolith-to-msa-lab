package com.beomsoo.shop.shared.domain

/**
 * 비즈니스 규칙 위반. [type]은 HTTP 상태 코드로, [code]는 클라이언트가 분기할 수 있는 에러 코드로 쓰인다.
 */
abstract class BusinessException(
    val type: ErrorType,
    val code: String,
    message: String,
) : RuntimeException(message)

enum class ErrorType {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
}

class InvalidInputException(message: String) : BusinessException(ErrorType.INVALID_INPUT, "INVALID_INPUT", message)
