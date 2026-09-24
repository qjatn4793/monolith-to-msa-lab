/**
 * 주문과 주문 상태 흐름. 다른 모듈을 조율하는 핵심 모듈.
 */
@ApplicationModule(
        displayName = "Order",
        allowedDependencies = {"member :: api", "catalog :: api", "inventory :: api", "payment :: api"}
)
package com.beomsoo.shop.order;

import org.springframework.modulith.ApplicationModule;
