/**
 * 상품 정보. 이름, 가격, 설명처럼 자주 읽히고 드물게 바뀌는 데이터.
 */
@ApplicationModule(
        displayName = "Catalog",
        allowedDependencies = {"shared"}
)
package com.beomsoo.shop.catalog;

import org.springframework.modulith.ApplicationModule;
