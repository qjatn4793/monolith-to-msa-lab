/**
 * 공유 커널 (ADR-0008). 모든 모듈이 함께 쓰는 최소한의 값 객체와 공통 기반.
 * 여기에 들어오는 코드는 MSA 전환 후 공통 라이브러리가 된다. 비즈니스 규칙은 두지 않는다.
 */
@ApplicationModule(
        displayName = "Shared Kernel",
        type = ApplicationModule.Type.OPEN
)
package com.beomsoo.shop.shared;

import org.springframework.modulith.ApplicationModule;
