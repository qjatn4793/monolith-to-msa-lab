package com.beomsoo.shop.inventory.domain

import com.beomsoo.shop.shared.domain.BusinessException
import com.beomsoo.shop.shared.domain.ErrorType
import java.util.UUID

class StockNotFoundException(productId: UUID) :
    BusinessException(ErrorType.NOT_FOUND, "STOCK_NOT_FOUND", "재고 정보가 없습니다: $productId")

class InsufficientStockException(productId: UUID, requested: Int, available: Int) :
    BusinessException(
        ErrorType.CONFLICT,
        "INSUFFICIENT_STOCK",
        "재고가 부족합니다: product=$productId, requested=$requested, available=$available",
    )

class ReservedStockExceededException(productId: UUID, requested: Int, reserved: Int) :
    BusinessException(
        ErrorType.CONFLICT,
        "RESERVED_STOCK_EXCEEDED",
        "예약된 수량을 넘어서 확정하거나 해제할 수 없습니다: product=$productId, requested=$requested, reserved=$reserved",
    )
