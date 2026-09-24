package com.beomsoo.shop.catalog.domain

import com.beomsoo.shop.shared.domain.BusinessException
import com.beomsoo.shop.shared.domain.ErrorType

class ProductNotFoundException(id: ProductId) :
    BusinessException(ErrorType.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다: $id")

class ProductAlreadyStoppedException(id: ProductId) :
    BusinessException(ErrorType.CONFLICT, "PRODUCT_ALREADY_STOPPED", "이미 판매 중지된 상품입니다: $id")

class ProductAlreadyOnSaleException(id: ProductId) :
    BusinessException(ErrorType.CONFLICT, "PRODUCT_ALREADY_ON_SALE", "이미 판매 중인 상품입니다: $id")
