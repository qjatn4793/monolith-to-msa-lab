package com.beomsoo.shop.shared.application

import com.beomsoo.shop.shared.domain.InvalidInputException

data class PageQuery(val page: Int, val size: Int) {

    init {
        if (page < 0) throw InvalidInputException("page는 0 이상이어야 합니다: $page")
        if (size !in 1..MAX_SIZE) throw InvalidInputException("size는 1 이상 $MAX_SIZE 이하여야 합니다: $size")
    }

    companion object {
        const val MAX_SIZE = 100
    }
}

data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int get() = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()

    fun <R> map(transform: (T) -> R): PageResult<R> = PageResult(content.map(transform), page, size, totalElements)
}
