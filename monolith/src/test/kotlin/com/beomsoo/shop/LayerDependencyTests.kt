package com.beomsoo.shop

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * 모듈 내부 레이어 의존 방향 검증 (ADR-0003, ADR-0004).
 *
 *   infrastructure ──▶ application ──▶ domain
 *          └──────────────────────────▶
 *
 * 모듈 사이의 경계는 ModularityTests가, 모듈 안쪽의 레이어는 이 테스트가 지킨다.
 *
 * allowEmptyShould(true): 아직 클래스가 없는 레이어가 있어도 규칙 자체는 유효하게 둔다.
 * 이 옵션이 없으면 ArchUnit은 검사 대상이 0개인 규칙을 실패로 처리한다.
 */
class LayerDependencyTests {

    private val classes: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.beomsoo.shop")

    @Test
    fun `domain은 application과 infrastructure를 모른다`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `domain은 Spring과 JPA에 의존하지 않는다`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `application은 infrastructure를 모른다`() {
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .check(classes)
    }

    /**
     * 모듈 간 협력이 일어나는 곳을 세 군데로 제한한다.
     * - infrastructure.adapter: 다른 모듈의 api를 호출하거나 자기 모듈의 이벤트를 발행하는 아웃바운드 어댑터
     * - infrastructure.facade: 자기 모듈의 api를 구현하는 인바운드 어댑터 (동기 호출)
     * - infrastructure.listener: 다른 모듈의 이벤트를 받는 인바운드 어댑터 (비동기)
     * 서비스, 도메인, 컨트롤러가 다른 모듈의 api를 직접 부르면 실패한다.
     * 그래서 모듈을 서비스로 떼어낼 때 바꿔야 할 곳이 adapter 패키지로 좁혀진다.
     */
    @Test
    fun `모듈의 공개 계약(api)은 adapter, facade, listener에서만 참조한다`() {
        classes().that().resideInAPackage("com.beomsoo.shop.*.api..")
            // onlyBeAccessed()는 메서드 호출, 필드 접근 같은 "접근"만 본다. 필드 타입이나 생성자 파라미터로
            // 선언만 한 경우는 잡지 못해서, 모든 종류의 의존을 보는 onlyHaveDependentClassesThat()을 쓴다.
            .should().onlyHaveDependentClassesThat()
            .resideInAnyPackage("..api..", "..infrastructure.adapter..", "..infrastructure.facade..", "..infrastructure.listener..")
            .check(classes)
    }

    @Test
    fun `공개 계약(api)은 도메인 객체를 노출하지 않는다`() {
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..application..", "..infrastructure..")
            .allowEmptyShould(true)
            .check(classes)
    }
}
