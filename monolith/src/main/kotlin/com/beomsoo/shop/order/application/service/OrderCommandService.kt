package com.beomsoo.shop.order.application.service

import com.beomsoo.shop.order.application.port.`in`.CancelOrderUseCase
import com.beomsoo.shop.order.application.port.`in`.PlaceOrderCommand
import com.beomsoo.shop.order.application.port.`in`.PlaceOrderUseCase
import com.beomsoo.shop.order.application.port.out.LoadOrdererPort
import com.beomsoo.shop.order.application.port.out.LoadProductPort
import com.beomsoo.shop.order.application.port.out.StockPort
import com.beomsoo.shop.order.application.port.out.StockReservation
import com.beomsoo.shop.order.domain.InactiveOrdererException
import com.beomsoo.shop.order.domain.Order
import com.beomsoo.shop.order.domain.OrderId
import com.beomsoo.shop.order.domain.OrderLine
import com.beomsoo.shop.order.domain.OrderNotFoundException
import com.beomsoo.shop.order.domain.OrderRepository
import com.beomsoo.shop.order.domain.OrdererNotFoundException
import com.beomsoo.shop.order.domain.OutOfStockException
import com.beomsoo.shop.order.domain.ProductNotOrderableException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 주문 유스케이스. 다른 모듈(member, catalog, inventory)과의 협력은 전부 아웃바운드 포트를 거친다.
 * 이 클래스는 다른 모듈의 존재를 모른다.
 *
 * 모놀리식에서는 이 메서드 하나가 DB 트랜잭션 하나다. 재고 예약까지 같은 트랜잭션에 묶이므로,
 * 중간에 실패하면 재고 예약도 함께 롤백된다. 모듈이 서비스로 분리되면 이 전제가 깨진다.
 */
@Service
@Transactional
class OrderCommandService(
    private val orderRepository: OrderRepository,
    private val loadOrdererPort: LoadOrdererPort,
    private val loadProductPort: LoadProductPort,
    private val stockPort: StockPort,
    private val clock: Clock,
) : PlaceOrderUseCase, CancelOrderUseCase {

    override fun place(command: PlaceOrderCommand): OrderId {
        val orderer = loadOrderer(command)
        val lines = createLines(command.items)
        val order = Order.place(orderer, lines, clock.instant())

        when (val reservation = stockPort.reserve(order.lines)) {
            StockReservation.Reserved -> Unit
            is StockReservation.OutOfStock -> throw OutOfStockException(reservation.productIds)
        }

        orderRepository.save(order)
        return order.id
    }

    override fun cancel(id: OrderId) {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        order.cancel()
        stockPort.release(order.lines)
        orderRepository.save(order)
    }

    private fun loadOrderer(command: PlaceOrderCommand) =
        (loadOrdererPort.loadOrderer(command.memberId) ?: throw OrdererNotFoundException(command.memberId))
            .also { if (!it.active) throw InactiveOrdererException(it.memberId) }
            .memberId

    /** 주문 시점의 상품명과 가격을 스냅샷으로 떠서 주문 상품을 만든다. */
    private fun createLines(items: List<PlaceOrderCommand.Item>): List<OrderLine> {
        val products = loadProductPort.loadProducts(items.map { it.productId }.toSet()).associateBy { it.productId }
        return items.map { item ->
            val product = products[item.productId] ?: throw ProductNotOrderableException(item.productId, "존재하지 않는 상품")
            if (!product.onSale) throw ProductNotOrderableException(item.productId, "판매 중지된 상품")
            OrderLine(product.productId, product.name, product.price, item.quantity)
        }
    }
}
