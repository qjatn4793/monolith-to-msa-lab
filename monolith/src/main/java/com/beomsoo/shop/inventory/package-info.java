/**
 * 재고. 쓰기 경합이 심한 데이터라 catalog와 분리했다 (ADR-0002).
 */
@ApplicationModule(
        displayName = "Inventory",
        allowedDependencies = {}
)
package com.beomsoo.shop.inventory;

import org.springframework.modulith.ApplicationModule;
