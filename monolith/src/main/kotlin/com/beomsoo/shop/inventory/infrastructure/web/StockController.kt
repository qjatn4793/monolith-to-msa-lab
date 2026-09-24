package com.beomsoo.shop.inventory.infrastructure.web

import com.beomsoo.shop.inventory.application.port.`in`.GetStockQuery
import com.beomsoo.shop.inventory.application.port.`in`.ReceiveStockUseCase
import com.beomsoo.shop.inventory.domain.Stock
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/stocks")
class StockController(
    private val receiveStockUseCase: ReceiveStockUseCase,
    private val getStockQuery: GetStockQuery,
) {

    @PostMapping("/{productId}/receive")
    fun receive(@PathVariable productId: UUID, @Valid @RequestBody request: ReceiveStockRequest): ResponseEntity<Unit> {
        receiveStockUseCase.receive(productId, request.quantity)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{productId}")
    fun get(@PathVariable productId: UUID): StockResponse = StockResponse.from(getStockQuery.getStock(productId))
}

data class ReceiveStockRequest(
    @field:Positive val quantity: Int,
)

data class StockResponse(
    val productId: UUID,
    val available: Int,
    val reserved: Int,
) {
    companion object {
        fun from(stock: Stock) = StockResponse(stock.productId, stock.available, stock.reserved)
    }
}
