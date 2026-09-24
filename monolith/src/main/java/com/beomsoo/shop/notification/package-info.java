/**
 * 알림 발송. 다른 모듈의 이벤트에 반응만 한다.
 */
@ApplicationModule(
        displayName = "Notification",
        allowedDependencies = {"shared", "order :: api", "member :: api"}
)
package com.beomsoo.shop.notification;

import org.springframework.modulith.ApplicationModule;
