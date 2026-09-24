package com.beomsoo.shop.learning

import com.beomsoo.shop.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.annotation.Transactional

/**
 * 학습 테스트: 왜 재고 부족을 예외가 아니라 결과값(ReservationResult)으로 돌려주는가.
 *
 * order의 주문 트랜잭션 안에서 inventory의 @Transactional 메서드를 호출하면, 둘은 같은 트랜잭션에 참여한다.
 * 안쪽 메서드가 예외를 던지면 Spring은 그 순간 트랜잭션 전체를 rollback-only로 표시한다.
 * 바깥에서 예외를 잡고 계속 진행해도, 커밋 시점에 UnexpectedRollbackException이 터진다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, RollbackOnlyLearningTest.Config::class)
class RollbackOnlyLearningTest {

    @Autowired
    lateinit var outer: Outer

    @Test
    fun `안쪽 트랜잭션에서 던진 예외를 바깥에서 잡아도 커밋할 수 없다`() {
        assertThrows<UnexpectedRollbackException> { outer.catchInnerException() }
    }

    @Test
    fun `실패를 결과값으로 돌려주면 바깥 트랜잭션은 정상적으로 커밋된다`() {
        assertDoesNotThrow { outer.checkInnerResult() }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun inner() = Inner()

        @Bean
        fun outer(inner: Inner) = Outer(inner)
    }

    open class Inner {
        @Transactional
        open fun reserveOrThrow(): Unit = throw IllegalStateException("재고 부족")

        @Transactional
        open fun reserveOrReject(): Boolean = false
    }

    open class Outer(private val inner: Inner) {
        @Transactional
        open fun catchInnerException() {
            try {
                inner.reserveOrThrow()
            } catch (e: IllegalStateException) {
                // 잡았으니 괜찮다고 생각하기 쉽다
            }
        }

        @Transactional
        open fun checkInnerResult() {
            if (!inner.reserveOrReject()) {
                // 거절됐다는 사실만 확인하고 다른 처리를 이어간다
            }
        }
    }
}
