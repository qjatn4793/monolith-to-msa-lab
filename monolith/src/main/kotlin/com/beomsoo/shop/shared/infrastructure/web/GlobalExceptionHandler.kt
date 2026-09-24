package com.beomsoo.shop.shared.infrastructure.web

import com.beomsoo.shop.shared.domain.BusinessException
import com.beomsoo.shop.shared.domain.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 모든 에러 응답을 RFC 9457 ProblemDetail 형식으로 통일한다.
 * 입력 형식 오류 같은 Spring MVC 예외는 부모 클래스가 같은 형식으로 처리한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ProblemDetail =
        problem(e.type.toHttpStatus(), e.code, e.message)

    /** 같은 데이터를 동시에 수정해서 낙관적 락 검사에 실패한 경우. 클라이언트가 재시도할 수 있다. */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: OptimisticLockingFailureException): ProblemDetail {
        log.info("동시 수정 충돌: {}", e.message)
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "다른 요청이 같은 데이터를 먼저 수정했습니다. 다시 시도해 주세요.")
    }

    /** 사전 검사를 통과했지만 DB 제약 조건(유니크 등)에 걸린 경우. 동시 요청 사이의 경합에서 생긴다. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException): ProblemDetail {
        log.warn("데이터 제약 조건 위반", e)
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "요청이 기존 데이터와 충돌합니다.")
    }

    private fun problem(status: HttpStatus, code: String, detail: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase).apply {
            setProperty("code", code)
        }

    private fun ErrorType.toHttpStatus(): HttpStatus = when (this) {
        ErrorType.INVALID_INPUT -> HttpStatus.BAD_REQUEST
        ErrorType.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorType.CONFLICT -> HttpStatus.CONFLICT
    }
}
