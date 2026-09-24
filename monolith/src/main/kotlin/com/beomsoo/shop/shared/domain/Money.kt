package com.beomsoo.shop.shared.domain

/**
 * 원화 금액. 이 프로젝트는 원화만 다루므로 통화 단위를 두지 않고, 원 단위 정수로 표현한다.
 */
@JvmInline
value class Money(val amount: Long) : Comparable<Money> {

    init {
        if (amount < 0) throw InvalidInputException("금액은 0 이상이어야 합니다: $amount")
    }

    operator fun plus(other: Money): Money = Money(Math.addExact(amount, other.amount))

    operator fun times(quantity: Int): Money = Money(Math.multiplyExact(amount, quantity.toLong()))

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    companion object {
        val ZERO = Money(0)
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO, Money::plus)
