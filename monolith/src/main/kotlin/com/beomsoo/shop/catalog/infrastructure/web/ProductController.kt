package com.beomsoo.shop.catalog.infrastructure.web

import com.beomsoo.shop.catalog.application.port.`in`.ChangeProductUseCase
import com.beomsoo.shop.catalog.application.port.`in`.GetProductQuery
import com.beomsoo.shop.catalog.application.port.`in`.RegisterProductCommand
import com.beomsoo.shop.catalog.application.port.`in`.RegisterProductUseCase
import com.beomsoo.shop.catalog.domain.ProductId
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import com.beomsoo.shop.shared.infrastructure.web.IdResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/products")
class ProductController(
    private val registerProductUseCase: RegisterProductUseCase,
    private val changeProductUseCase: ChangeProductUseCase,
    private val getProductQuery: GetProductQuery,
) {

    @PostMapping
    fun register(@Valid @RequestBody request: RegisterProductRequest): ResponseEntity<IdResponse> {
        val id = registerProductUseCase.register(
            RegisterProductCommand(name = request.name, price = request.price, description = request.description),
        )
        return ResponseEntity.created(URI.create("/products/$id")).body(IdResponse(id.value))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ProductResponse = ProductResponse.from(getProductQuery.getProduct(ProductId(id)))

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<ProductResponse> = getProductQuery.getProducts(PageQuery(page, size)).map(ProductResponse::from)

    @PatchMapping("/{id}/price")
    fun changePrice(@PathVariable id: UUID, @Valid @RequestBody request: ChangePriceRequest): ResponseEntity<Unit> {
        changeProductUseCase.changePrice(ProductId(id), request.price)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/stop-selling")
    fun stopSelling(@PathVariable id: UUID): ResponseEntity<Unit> {
        changeProductUseCase.stopSelling(ProductId(id))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/resume-selling")
    fun resumeSelling(@PathVariable id: UUID): ResponseEntity<Unit> {
        changeProductUseCase.resumeSelling(ProductId(id))
        return ResponseEntity.noContent().build()
    }
}
