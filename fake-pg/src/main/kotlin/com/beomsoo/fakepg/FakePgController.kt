package com.beomsoo.fakepg

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

@ConfigurationProperties("fake-pg")
data class FakePgBehavior(
    val delayMs: Long = 0,
    val approvalLimit: Long = 1_000_000,
    val declineRate: Double = 0.0,
    val errorRate: Double = 0.0,
)

data class ApprovalRequest(
    val idempotencyKey: String,
    val orderId: String,
    val amount: Long,
)

data class ApprovalResponse(
    val status: String,
    val transactionId: String? = null,
    val reason: String? = null,
)

@RestController
class FakePgController(initial: FakePgBehavior) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val behavior = AtomicReference(initial)

    /** 같은 멱등성 키로 다시 요청하면 처음 결과를 그대로 돌려준다. 실제 PG도 이렇게 중복 결제를 막는다. */
    private val results = ConcurrentHashMap<String, ApprovalResponse>()

    @PostMapping("/v1/payments")
    fun approve(@RequestBody request: ApprovalRequest): ResponseEntity<ApprovalResponse> {
        results[request.idempotencyKey]?.let {
            log.info("멱등성 키 재요청: key={}, 기존 결과={}", request.idempotencyKey, it.status)
            return ResponseEntity.ok(it)
        }

        val current = behavior.get()
        if (current.delayMs > 0) Thread.sleep(current.delayMs)

        val random = ThreadLocalRandom.current()
        if (random.nextDouble() < current.errorRate) {
            log.info("일부러 500 응답: key={}", request.idempotencyKey)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        val response = when {
            request.amount > current.approvalLimit -> ApprovalResponse("DECLINED", reason = "LIMIT_EXCEEDED")
            random.nextDouble() < current.declineRate -> ApprovalResponse("DECLINED", reason = "RANDOM_DECLINE")
            else -> ApprovalResponse("APPROVED", transactionId = "pg-${UUID.randomUUID()}")
        }
        val stored = results.putIfAbsent(request.idempotencyKey, response) ?: response
        log.info("결제 요청: key={}, amount={}, result={}", request.idempotencyKey, request.amount, stored.status)
        return ResponseEntity.ok(stored)
    }

    /** 실험 중에 PG의 동작을 바꾼다. 예: curl -X PUT localhost:9090/admin/behavior -H 'Content-Type: application/json' -d '{"delayMs": 3000}' */
    @PutMapping("/admin/behavior")
    fun changeBehavior(@RequestBody newBehavior: FakePgBehavior): FakePgBehavior {
        behavior.set(newBehavior)
        log.info("PG 동작 변경: {}", newBehavior)
        return newBehavior
    }

    @GetMapping("/admin/behavior")
    fun currentBehavior(): FakePgBehavior = behavior.get()
}
