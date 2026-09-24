package com.beomsoo.shop

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * 모듈 경계 검증 (ADR-0003, ADR-0005).
 *
 * - 모듈 사이에 순환 의존이 없어야 한다.
 * - 다른 모듈은 공개 계약(api 패키지)만 참조할 수 있다.
 * - 각 모듈은 package-info.java의 allowedDependencies에 선언한 모듈에만 의존할 수 있다.
 */
class ModularityTests {

    private val modules = ApplicationModules.of(MonolithApplication::class.java)

    @Test
    fun `모듈 경계를 지킨다`() {
        modules.verify()
    }

    @Test
    fun `모듈 구조 문서를 생성한다`() {
        // build/spring-modulith-docs 에 모듈 다이어그램(PlantUML)과 모듈 설명(Canvas)을 만든다.
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases()
    }
}
