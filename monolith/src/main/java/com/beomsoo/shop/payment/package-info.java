/**
 * 결제와 외부 PG 연동.
 */
@ApplicationModule(
        displayName = "Payment",
        allowedDependencies = {"shared"}
)
package com.beomsoo.shop.payment;

import org.springframework.modulith.ApplicationModule;
