package com.beomsoo.shop.order.domain

import com.beomsoo.shop.shared.domain.BusinessException
import com.beomsoo.shop.shared.domain.ErrorType
import java.util.UUID

class OrderNotFoundException(id: OrderId) :
    BusinessException(ErrorType.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다: $id")

class OrderNotCancellableException(id: OrderId, status: OrderStatus) :
    BusinessException(ErrorType.CONFLICT, "ORDER_NOT_CANCELLABLE", "취소할 수 없는 주문입니다: $id ($status)")

class OrdererNotFoundException(memberId: UUID) :
    BusinessException(ErrorType.NOT_FOUND, "ORDERER_NOT_FOUND", "주문자를 찾을 수 없습니다: $memberId")

class InactiveOrdererException(memberId: UUID) :
    BusinessException(ErrorType.CONFLICT, "ORDERER_INACTIVE", "주문할 수 없는 회원입니다: $memberId")

class ProductNotOrderableException(productId: UUID, reason: String) :
    BusinessException(ErrorType.CONFLICT, "PRODUCT_NOT_ORDERABLE", "주문할 수 없는 상품입니다: $productId ($reason)")

class OutOfStockException(productIds: List<UUID>) :
    BusinessException(ErrorType.CONFLICT, "OUT_OF_STOCK", "재고가 부족한 상품이 있습니다: $productIds")
