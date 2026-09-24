package com.beomsoo.shop.order.infrastructure.web

import com.beomsoo.shop.order.application.port.`in`.CancelOrderUseCase
import com.beomsoo.shop.order.application.port.`in`.GetOrderQuery
import com.beomsoo.shop.order.application.port.`in`.PlaceOrderCommand
import com.beomsoo.shop.order.application.port.`in`.PlaceOrderUseCase
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import com.beomsoo.shop.shared.infrastructure.web.IdResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/orders")
class OrderController(
    private val placeOrderUseCase: PlaceOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val getOrderQuery: GetOrderQuery,
) {

    @PostMapping
    fun place(@Valid @RequestBody request: PlaceOrderRequest): ResponseEntity<IdResponse> {
        val id = placeOrderUseCase.place(
            PlaceOrderCommand(
                memberId = request.memberId,
                items = request.items.map { PlaceOrderCommand.Item(it.productId, it.quantity) },
            ),
        )
        return ResponseEntity.created(URI.create("/orders/$id")).body(IdResponse(id.value))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): OrderResponse = OrderResponse.from(getOrderQuery.getOrder(OrderId(id)))

    @GetMapping
    fun listOfMember(
        @RequestParam memberId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<OrderResponse> = getOrderQuery.getOrdersOfMember(memberId, PageQuery(page, size)).map(OrderResponse::from)

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: UUID): ResponseEntity<Unit> {
        cancelOrderUseCase.cancel(OrderId(id))
        return ResponseEntity.noContent().build()
    }
}
