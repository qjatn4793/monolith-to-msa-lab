package com.beomsoo.shop.catalog.application.service

import com.beomsoo.shop.catalog.application.port.`in`.ChangeProductUseCase
import com.beomsoo.shop.catalog.application.port.`in`.RegisterProductCommand
import com.beomsoo.shop.catalog.application.port.`in`.RegisterProductUseCase
import com.beomsoo.shop.catalog.domain.Product
import com.beomsoo.shop.catalog.domain.ProductId
import com.beomsoo.shop.catalog.domain.ProductNotFoundException
import com.beomsoo.shop.catalog.domain.ProductRepository
import com.beomsoo.shop.shared.domain.Money
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional
class ProductCommandService(
    private val productRepository: ProductRepository,
    private val clock: Clock,
) : RegisterProductUseCase, ChangeProductUseCase {

    override fun register(command: RegisterProductCommand): ProductId {
        val product = Product.register(command.name, Money(command.price), command.description, clock.instant())
        productRepository.save(product)
        return product.id
    }

    override fun changePrice(id: ProductId, newPrice: Long) = modify(id) { it.changePrice(Money(newPrice)) }

    override fun stopSelling(id: ProductId) = modify(id) { it.stopSelling() }

    override fun resumeSelling(id: ProductId) = modify(id) { it.resumeSelling() }

    private fun modify(id: ProductId, change: (Product) -> Unit) {
        val product = productRepository.findById(id) ?: throw ProductNotFoundException(id)
        change(product)
        productRepository.save(product)
    }
}
