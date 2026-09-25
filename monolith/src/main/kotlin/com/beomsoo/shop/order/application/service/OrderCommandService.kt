package com.beomsoo.shop.order.application.service

import com.beomsoo.shop.order.application.port.`in`.CancelOrderUseCase
import com.beomsoo.shop.order.application.port.`in`.PlaceOrderCommand
import com.beomsoo.shop.order.application.port.`in`.PlaceOrderResult
import com.beomsoo.shop.order.application.port.`in`.PlaceOrderUseCase
import com.beomsoo.shop.order.application.port.out.LoadOrdererPort
import com.beomsoo.shop.order.application.port.out.LoadProductPort
import com.beomsoo.shop.order.application.port.out.OrderEventPublisher
import com.beomsoo.shop.order.application.port.out.PaymentResult
import com.beomsoo.shop.order.application.port.out.RequestPaymentPort
import com.beomsoo.shop.order.application.port.out.StockPort
import com.beomsoo.shop.order.application.port.out.StockReservation
import com.beomsoo.shop.order.domain.CancelReason
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
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock

/**
 * 주문 유스케이스. 다른 모듈(member, catalog, inventory, payment)과의 협력은 전부 아웃바운드 포트를 거친다.
 *
 * 트랜잭션 경계를 @Transactional 대신 TransactionOperations로 코드에 드러낸다 (ADR-0006).
 *
 *   [TX1] 주문 생성(PENDING) + 재고 예약
 *         결제 요청                              ← 트랜잭션 밖. PG가 느려도 DB 커넥션을 붙잡지 않는다
 *   [TX2] 승인 → 주문 확정 + 재고 차감 확정 + 이벤트 발행
 *         실패 → 주문 취소 + 재고 예약 해제 + 이벤트 발행
 *
 * TX1과 TX2 사이에 프로세스가 죽으면 주문이 PENDING으로 남는다. 알려진 한계다 (ADR-0012).
 */
@Service
class OrderCommandService(
    private val orderRepository: OrderRepository,
    private val loadOrdererPort: LoadOrdererPort,
    private val loadProductPort: LoadProductPort,
    private val stockPort: StockPort,
    private val requestPaymentPort: RequestPaymentPort,
    private val eventPublisher: OrderEventPublisher,
    private val tx: TransactionOperations,
    private val clock: Clock,
) : PlaceOrderUseCase, CancelOrderUseCase {

    override fun place(command: PlaceOrderCommand): PlaceOrderResult {
        val order = tx.execute { createPendingOrder(command) }!!

        val payment = requestPaymentPort.pay(order.id, order.totalAmount)

        return tx.execute { completeOrder(order.id, payment) }!!
    }

    override fun cancel(id: OrderId) {
        tx.executeWithoutResult {
            val order = load(id)
            eventPublisher.publish(order.cancel(CancelReason.REQUESTED, clock.instant()))
            stockPort.release(order.lines)
            orderRepository.save(order)
        }
    }

    /** [TX1] 주문자와 상품을 확인하고, 재고를 예약하고, PENDING 주문을 저장한다. */
    private fun createPendingOrder(command: PlaceOrderCommand): Order {
        val memberId = loadOrderer(command)
        val lines = createLines(command.items)
        val order = Order.place(memberId, lines, clock.instant())

        when (val reservation = stockPort.reserve(order.lines)) {
            StockReservation.Reserved -> Unit
            is StockReservation.OutOfStock -> throw OutOfStockException(reservation.productIds)
        }

        orderRepository.save(order)
        return order
    }

    /** [TX2] 결제 결과에 따라 주문을 확정하거나 취소한다. 이벤트도 같은 트랜잭션에서 발행한다. */
    private fun completeOrder(id: OrderId, payment: PaymentResult): PlaceOrderResult {
        val order = load(id)
        when (payment) {
            PaymentResult.Paid -> {
                eventPublisher.publish(order.confirm(clock.instant()))
                stockPort.confirm(order.lines)
            }
            is PaymentResult.Failed -> {
                eventPublisher.publish(order.cancel(CancelReason.PAYMENT_FAILED, clock.instant()))
                stockPort.release(order.lines)
            }
        }
        orderRepository.save(order)
        return PlaceOrderResult(order.id, order.status)
    }

    private fun load(id: OrderId): Order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)

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
