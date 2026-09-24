package com.beomsoo.shop

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
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

    @Test
    fun `공개 계약(api)은 도메인 객체를 노출하지 않는다`() {
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..application..", "..infrastructure..")
            .allowEmptyShould(true)
            .check(classes)
    }
}
