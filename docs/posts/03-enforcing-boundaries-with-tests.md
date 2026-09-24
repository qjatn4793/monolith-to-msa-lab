# [모놀리식에서 MSA로 #3] 경계는 사람이 아니라 테스트가 지킨다: Spring Modulith와 ArchUnit

> 시리즈 코드는 전부 [monolith-to-msa-lab 저장소](https://github.com/qjatn4793/monolith-to-msa-lab)에 있다.
> 이 글의 에러 메시지는 전부 실제로 위반 코드를 넣고 테스트를 돌려서 얻은 출력이다. 위반 코드는 확인 후 지웠다.

[#0](00-how-companies-design-monoliths.md)에서 정리한 원칙 중 하나는 **"경계는 사람이 아니라 도구가 지킨다"**였다. "다른 모듈의 내부 클래스는 쓰지 말자"는 규칙은 문서에 적는 순간부터 깨지기 시작한다. 마감이 급한 날 누군가는 import 한 줄로 해결한다.

[#1](01-domain-and-bounded-contexts.md)에서 모듈 사이의 경계를 긋고, [#2](02-aggregates-and-hexagonal.md)에서 모듈 안쪽의 계층을 나눴다. 이번 글은 그 경계를 **테스트로 강제하는 방법**이다.

미리 말하면, 이번 글에서 가장 중요한 내용은 도구 사용법이 아니다. **규칙을 테스트로 만들었는데 위반을 잡지 못했던 일**이 두 번 있었다. 한 번은 규칙을 잘못 썼고, 한 번은 도구 자체가 볼 수 없는 영역이었다. 그 과정에서 얻은 교훈이 이번 글의 핵심이다.

<!-- TODO(작성자): 팀에서 "이건 이렇게 하지 말자"고 정했던 규칙이 흐지부지된 경험이 있다면 한 문단 -->

---

## 0. 두 개의 도구, 두 종류의 경계

지킬 경계는 두 종류다.

| 경계 | 예 | 도구 | 테스트 |
|---|---|---|---|
| **모듈 사이** | order가 inventory의 내부 클래스를 쓰면 안 된다 | Spring Modulith | `ModularityTests` |
| **모듈 안쪽 계층** | domain이 Spring이나 JPA에 의존하면 안 된다 | ArchUnit | `LayerDependencyTests` |
| (위 둘의 빈틈) | Kotlin 값 클래스를 통한 의존 | import 문 검사 | `SourceImportRulesTests` |

Modulith와 ArchUnit은 **컴파일된 바이트코드를 분석**한다. 코드를 실행하지 않으니 DB도 Spring 컨텍스트도 필요 없고, 두 테스트 클래스를 합쳐 1~2초 안에 끝난다. 세 번째 줄은 처음에는 없었다. 왜 필요해졌는지는 7절에서 다룬다.

---

## 1. Spring Modulith: 패키지가 곧 모듈이다

Spring Modulith의 규칙은 단순하다.

- 메인 클래스(`MonolithApplication`)가 있는 패키지의 **바로 아래 패키지 하나하나가 모듈**이다. `com.beomsoo.shop.order`, `com.beomsoo.shop.inventory`처럼.
- 모듈의 **하위 패키지는 기본적으로 내부(internal)**다. 다른 모듈이 참조하면 위반이다.
- 공개하고 싶은 하위 패키지는 `@NamedInterface`로 표시한다.
- 모듈마다 의존해도 되는 모듈을 `@ApplicationModule(allowedDependencies = ...)`로 선언할 수 있다.

이 프로젝트에서는 각 모듈의 `api` 패키지만 공개했다.

```java
// inventory/api/package-info.java
@NamedInterface("api")
package com.beomsoo.shop.inventory.api;
```

그리고 모듈마다 허용 의존을 선언했다.

```java
// order/package-info.java
@ApplicationModule(
        displayName = "Order",
        allowedDependencies = {"shared", "member :: api", "catalog :: api", "inventory :: api", "payment :: api"}
)
package com.beomsoo.shop.order;
```

`"inventory :: api"`는 "inventory 모듈 중에서도 `api`라는 이름의 공개 인터페이스만"이라는 뜻이다.

하나 걸리는 게 있었다. 이 선언은 **패키지에 붙이는 어노테이션**인데, Kotlin에는 패키지 어노테이션이 없다. 그래서 이 프로젝트는 코드는 Kotlin으로 쓰고 `package-info.java`만 Java로 둔다. Kotlin JVM 플러그인은 `src/main/java`의 Java 파일도 함께 컴파일하기 때문에 별도 설정 없이 동작한다.

---

## 2. 테스트는 한 줄이다

```kotlin
class ModularityTests {

    private val modules = ApplicationModules.of(MonolithApplication::class.java)

    @Test
    fun `모듈 경계를 지킨다`() {
        modules.verify()
    }
}
```

`verify()`가 검사하는 것은 세 가지다.

1. 모듈 사이에 **순환 의존**이 없는가
2. 다른 모듈의 **내부 타입**(공개하지 않은 패키지)을 참조하지 않는가
3. 선언한 **허용 의존** 밖의 모듈에 의존하지 않는가

### 실제로 잡아낸 위반 1: 다른 모듈의 도메인 클래스 참조

P0에서 규칙이 동작하는지 보려고, order의 인프라 코드가 inventory의 도메인 클래스(`inventory.domain.Stock`)를 필드로 갖게 해봤다.

```
Module 'order' depends on non-exposed type com.beomsoo.shop.inventory.domain.Stock within module 'inventory'!
Field <com.beomsoo.shop.order.infrastructure.Violation.stock> has type <com.beomsoo.shop.inventory.domain.Stock> in (Violation.kt:0)
```

"non-exposed type", 즉 공개하지 않은 타입이라고 정확히 짚는다. 이 import 한 줄이 들어가는 순간, 나중에 inventory를 서비스로 떼어낼 때 order도 함께 고쳐야 하는 결합이 생긴다. 그걸 빌드 단계에서 막는다.

---

## 3. 공유 커널의 함정: OPEN 모듈도 선언해야 한다

P1에서 모든 모듈이 함께 쓰는 공유 커널 `shared`를 만들었다. `Money`, 예외 기반 타입, ID 생성 함수 같은 것들이다. 모든 모듈이 하위 패키지(`shared.domain` 등)를 써야 하니 `Type.OPEN`으로 선언했다. OPEN 모듈은 하위 패키지까지 전부 공개된다.

```java
@ApplicationModule(displayName = "Shared Kernel", type = ApplicationModule.Type.OPEN)
package com.beomsoo.shop.shared;
```

"공개했으니 누구나 쓸 수 있겠지" 하고 테스트를 돌렸더니 실패했다.

```
Module 'member' depends on module 'shared' via com.beomsoo.shop.member.domain.DuplicateEmailException
  -> com.beomsoo.shop.shared.domain.ErrorType. Allowed targets: none.
Module 'member' depends on module 'shared' via com.beomsoo.shop.member.domain.DuplicateEmailException
  -> com.beomsoo.shop.shared.domain.BusinessException. Allowed targets: none.
```

`Allowed targets: none.` member는 P0에서 `allowedDependencies = {}`(아무것도 의존하지 않음)로 선언해 두었기 때문이다. **OPEN은 "무엇을 공개하는가"이고, allowedDependencies는 "누구에게 의존해도 되는가"다.** 둘은 별개였다. 모든 모듈의 허용 목록에 `"shared"`를 추가해서 해결했다.

오히려 좋은 결과라고 생각한다. 공유 커널에 대한 의존도 **명시적으로 선언해야** 하니, 어떤 모듈이 공유 커널을 쓰는지 한눈에 보인다. 공유 커널은 모든 모듈이 의존하는 만큼 함부로 커지면 안 된다. 그래서 들어갈 수 있는 것을 제한했다 ([ADR-0008](../adr/0008-shared-kernel.md)).

| 들어갈 수 있는 것 | 들어갈 수 없는 것 |
|---|---|
| 불변 값 객체 (`Money`) | 비즈니스 규칙 (예: 할인 정책) |
| 예외 기반 타입 (`BusinessException`) | 특정 모듈의 개념 (예: `MemberId`) |
| 기술적 공통 기반 (ID 생성, 페이지, 에러 응답 형식) | 상태를 가진 서비스 |

---

## 4. 선언과 실제를 비교해 보기

Modulith는 런타임에도 모듈 구조를 보여준다. `spring-modulith-actuator`를 넣으면 `/actuator/modulith` 엔드포인트가 생긴다. 실제 응답에서 order 부분만 보면 이렇다.

```json
"order": {
  "type": "closed",
  "allowedDependencies": ["shared", "member :: api", "catalog :: api", "inventory :: api", "payment :: api"],
  "dependencies": [
    { "target": "shared",    "types": ["DEFAULT"] },
    { "target": "inventory", "types": ["USES_COMPONENT"] },
    { "target": "catalog",   "types": ["USES_COMPONENT"] },
    { "target": "member",    "types": ["USES_COMPONENT"] }
  ]
}
```

`allowedDependencies`에는 `payment :: api`가 있는데, 실제 `dependencies`에는 payment가 없다. 결제는 아직 구현하지 않았기 때문이다(#4에서 구현한다). **선언은 상한선이고, 실제 의존은 코드가 결정한다.** 둘을 나란히 볼 수 있어서 설계와 구현이 어긋나는지 바로 확인할 수 있다.

`USES_COMPONENT`는 order가 다른 모듈의 Spring 빈(`MemberFacade` 등)을 주입받아 쓴다는 뜻이다. `shared`는 빈이 아니라 타입(`Money` 등)만 쓰니 `DEFAULT`로 나온다.

같은 정보로 모듈 다이어그램도 만들 수 있다. 테스트에 `Documenter`를 한 줄 추가하면 PlantUML 파일이 생성된다.

```kotlin
Documenter(modules)
    .writeModulesAsPlantUml()
    .writeIndividualModulesAsPlantUml()
    .writeModuleCanvases()
```

<!-- TODO(작성자): build/spring-modulith-docs/components.puml을 렌더링한 이미지 첨부 -->

---

## 5. ArchUnit: 모듈 안쪽의 계층 규칙

Modulith는 모듈 **사이**만 본다. 모듈 **안쪽**에서 도메인이 JPA에 의존하든, 서비스가 인프라 클래스를 직접 쓰든 Modulith는 관심이 없다. 그건 ArchUnit으로 검사한다.

```kotlin
@Test
fun `domain은 Spring과 JPA에 의존하지 않는다`() {
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
        .allowEmptyShould(true)
        .check(classes)
}
```

`LayerDependencyTests`에 있는 규칙은 다섯 개다.

| 규칙 | 지키려는 것 |
|---|---|
| domain은 application, infrastructure를 모른다 | 의존 방향 |
| domain은 Spring, JPA에 의존하지 않는다 | 도메인의 순수성 ([#2](02-aggregates-and-hexagonal.md)의 3절) |
| application은 infrastructure를 모른다 | 의존 방향 |
| api는 domain, application, infrastructure를 노출하지 않는다 | 공개 계약에 내부 타입이 새지 않게 |
| api는 api 자신, adapter, facade에서만 참조한다 | 모듈 간 호출 위치 제한 (6절) |

### 실제로 잡아낸 위반 2: 도메인에 Spring 어노테이션

도메인 클래스에 `@Component`를 붙여봤다.

```
Rule 'no classes that reside in a package '..domain..' should depend on classes that reside in any package
['org.springframework..', 'jakarta.persistence..']' was violated (1 times):
Class <com.beomsoo.shop.payment.domain.SpringInDomain> is annotated with <org.springframework.stereotype.Component> in (SpringInDomain.kt:0)
```

### `allowEmptyShould(true)`는 왜 붙였나

ArchUnit은 **검사 대상이 0개인 규칙을 실패로 처리**한다. 패키지 패턴을 잘못 써서 아무것도 검사하지 않는 규칙이 조용히 통과하는 걸 막기 위한 안전장치다. P0에서는 클래스가 하나도 없어서 이 옵션 없이는 모든 규칙이 실패했다.

지금은 대부분의 규칙에 검사 대상이 있다. 그래도 옵션을 남겨둔 이유가 있다. 아직 해당 레이어가 없는 모듈(payment, notification)이 있기 때문이다. 대신 이 옵션을 켜면 ArchUnit의 안전장치 하나를 끄는 셈이다. 그 대가를 바로 다음 절에서 치렀다.

---

## 6. 규칙을 만들었는데, 위반을 잡지 못했다

### 발단: 설명과 실제가 달랐다

#0을 쓸 때 이런 규칙을 적었다. "모듈 간 동기 호출은 `infrastructure/adapter` 한 곳에서만 일어난다. 전부 테스트로 강제한다."

#0의 구조도를 실제 코드와 비교하다가 알게 됐다. **이 규칙을 검사하는 테스트가 없었다.** Modulith가 검사하는 건 "다른 모듈의 `api`만 참조하는가"까지다. order의 **서비스**가 `member.api.MemberFacade`를 직접 주입받아도, order는 `member :: api`에 의존해도 되니 Modulith는 통과시킨다. 그렇게 되면 member를 서비스로 떼어낼 때 어댑터 하나가 아니라 서비스 코드까지 고쳐야 한다.

지금 코드가 이 규칙을 지키고 있던 건 **우연**이었다.

### 1차 시도: 통과했다

ArchUnit 규칙을 추가했다. "`api` 패키지는 `api` 자신, `infrastructure.adapter`, `infrastructure.facade`에서만 접근할 수 있다."

```kotlin
classes().that().resideInAPackage("com.beomsoo.shop.*.api..")
    .should().onlyBeAccessed().byClassesThat()
    .resideInAnyPackage("..api..", "..infrastructure.adapter..", "..infrastructure.facade..")
    .check(classes)
```

테스트는 통과했다. 여기서 끝냈다면 규칙이 생겼다고 믿었을 것이다.

### 확인: 위반을 넣었는데도 통과했다

규칙이 정말 동작하는지 보려고, order의 서비스 패키지에 member의 파사드를 필드로 가진 클래스를 넣었다.

```kotlin
// order/application/service/Violation.kt (확인용)
class Violation(private val memberFacade: MemberFacade)
```

결과는 이랬다.

```
BUILD SUCCESSFUL
== LayerDependencyTests: no failure
== ModularityTests:      no failure
```

**둘 다 통과했다.** Modulith가 통과한 건 예상한 대로다. 문제는 방금 만든 ArchUnit 규칙이 위반을 잡지 못했다는 점이다.

### 원인: "접근"과 "의존"은 다르다

`onlyBeAccessed()`는 **접근(access)**만 본다. ArchUnit에서 접근은 메서드 호출, 필드 읽기와 쓰기, 생성자 호출이다. 확인용 클래스는 `MemberFacade`를 필드 타입과 생성자 파라미터로 **선언만** 했지, 메서드를 호출하지는 않았다. 그래서 접근이 0건이었다.

하지만 실제로 문제가 되는 건 **의존(dependency)**이다. 필드 타입으로 선언하기만 해도 이 클래스는 member 모듈 없이는 컴파일되지 않는다. 모든 종류의 의존을 보는 `onlyHaveDependentClassesThat()`으로 바꿨다.

```kotlin
classes().that().resideInAPackage("com.beomsoo.shop.*.api..")
    .should().onlyHaveDependentClassesThat()
    .resideInAnyPackage("..api..", "..infrastructure.adapter..", "..infrastructure.facade..")
    .check(classes)
```

같은 위반 코드로 다시 돌렸다.

```
Rule 'classes that reside in a package 'com.beomsoo.shop.*.api..' should only have dependent classes that reside in any package
['..api..', '..infrastructure.adapter..', '..infrastructure.facade..']' was violated (2 times):
Constructor <...order.application.service.Violation.<init>(...member.api.MemberFacade)> has parameter of type <...MemberFacade>
Field <...order.application.service.Violation.memberFacade> has type <...MemberFacade>
```

이번에는 잡혔다. 위반 코드를 지우자 다시 통과했다.

### 두 도구는 서로 다른 것을 지킨다

같은 위반 코드에 대한 결과를 나란히 놓으면 이렇다.

| 위반 | ModularityTests | LayerDependencyTests |
|---|---|---|
| order가 inventory의 **내부** 클래스(`inventory.domain.Stock`) 참조 | **실패** | (해당 규칙 없음) |
| order의 **서비스**가 member의 **공개** 파사드(`member.api.MemberFacade`) 참조 | 통과 | **실패** |

Modulith는 **"어떤 모듈의 어떤 패키지를"** 참조하는지 본다. ArchUnit 규칙은 **"모듈 안의 어느 계층에서"** 참조하는지 본다. 둘 중 하나만 있었다면 위 표의 한 줄은 뚫려 있었을 것이다.

### 교훈: 규칙 테스트도 실패하는 걸 먼저 봐야 한다

TDD에서는 테스트가 **실패하는 걸 먼저 확인**하고 나서 통과시킨다. 실패하는 걸 본 적 없는 테스트는 아무것도 검사하지 않을 수도 있기 때문이다.

아키텍처 규칙 테스트도 똑같았다. 규칙이 통과했다는 건 "위반이 없다"일 수도, "규칙이 아무것도 못 본다"일 수도 있다. 5절의 `allowEmptyShould(true)`까지 켜져 있으면 ArchUnit의 안전장치도 없다.

그래서 이 일 이후 **모든 규칙에 위반 코드를 하나씩 넣어서 실패하는 걸 확인했다.** `LayerDependencyTests`의 규칙 다섯 개 전부 각자의 위반을 잡았다. 그런데 이 확인을 하다가 더 큰 빈틈을 발견했다.

---

## 7. 두 도구 모두 볼 수 없는 것: Kotlin 값 클래스

### 이상한 실패 메시지

"api는 도메인 객체를 노출하지 않는다" 규칙을 확인하려고, member의 `api` 패키지에 도메인의 `MemberId`를 담은 데이터 클래스를 넣었다. 규칙은 실패했다. 그런데 메시지가 이상했다.

```
Method <...member.api.ViolationC.equals(java.lang.Object)> calls method <...member.domain.MemberId.equals-impl0(java.util.UUID, java.util.UUID)>
Method <...member.api.ViolationC.hashCode()> calls method <...member.domain.MemberId.hashCode-impl(java.util.UUID)>
```

필드 타입이 `MemberId`라서 잡힌 게 아니었다. 데이터 클래스가 자동으로 만든 `equals`와 `hashCode`가 `MemberId`의 메서드를 호출해서 잡혔다. 그렇다면 `equals`가 없는 일반 클래스라면?

```kotlin
class ViolationD(val id: MemberId)   // member/api 패키지 (확인용)
```

**통과했다.** 컴파일된 클래스를 `javap`로 열어보면 이유가 보인다.

```
public final class com.beomsoo.shop.member.api.ViolationD {
  private final java.util.UUID id;
  public final java.util.UUID getId-KPO-LuI();
  ...
}
```

`MemberId`가 어디에도 없다. [#2](02-aggregates-and-hexagonal.md)에서 ID를 `value class`로 만든 이유가 "런타임에 감싼 값으로 컴파일되어 비용이 거의 없다"였다. 바로 그 특성 때문에 **바이트코드에서 타입이 지워진다.** 바이트코드를 분석하는 도구는 볼 수 없다.

### Modulith도 마찬가지였다

ArchUnit만의 문제가 아니다. Modulith도 바이트코드를 분석하기 때문이다. order의 어댑터가 member의 **내부** 값 클래스를 필드로 갖게 하고, 같은 자리에 일반 클래스를 넣어 비교했다.

| order가 필드로 가진 member의 내부 타입 | 바이트코드에 남는 타입 | ModularityTests |
|---|---|---|
| `member.domain.Member` (일반 클래스) | `Member` | **실패** (`depends on non-exposed type ...Member`) |
| `member.domain.MemberId` (값 클래스) | `UUID` | **통과** |

이 프로젝트는 ID와 `Email`, `Money`를 전부 값 클래스로 만들었다. 즉 **경계 검사가 가장 자주 쓰이는 타입에서 뚫려 있었다.** 이 상태로 member를 서비스로 떼어내면, order의 코드가 `member.domain.MemberId`를 import 하고 있어서 컴파일이 깨진다. 모놀리식에서는 아무 테스트도 이를 알려주지 않는다.

### 해결: import 문은 지워지지 않는다

바이트코드에서는 지워져도 **소스 코드의 import 문에는 남는다.** 그래서 import 문을 읽는 테스트를 추가했다.

```kotlin
// SourceImportRulesTests.kt
@Test
fun `다른 모듈은 api 패키지만 import 한다`() {
    val violations = imports.filter {
        it.targetModule != it.module &&
            it.targetModule != "shared" &&
            !it.target.startsWith("$base${it.targetModule}.api.")
    }
    assertTrue(violations.isEmpty(), "다른 모듈의 내부 패키지를 import 했다:\n" + violations.joinToString("\n"))
}
```

앞의 두 위반을 넣고 돌린 결과다.

```
다른 모듈의 내부 패키지를 import 했다:
order/infrastructure/adapter/ViolationE.kt → com.beomsoo.shop.member.domain.MemberId

공개 계약(api)이 내부 타입을 import 했다:
member/api/ViolationD.kt → com.beomsoo.shop.member.domain.MemberId
```

같은 위반에 대해 Modulith와 ArchUnit은 여전히 통과한다. 세 테스트가 각자 다른 빈틈을 막는다.

이 방법에도 한계는 있다. import 없이 `com.beomsoo.shop.member.domain.MemberId`처럼 전체 이름을 코드에 직접 쓰면 잡지 못한다. Kotlin 소스를 구문 분석하는 [Konsist](https://docs.konsist.lemonappdev.com/) 같은 도구를 쓰면 더 정확하게 검사할 수 있다. 다만 지금은 import 검사로 충분하다고 판단했다. 전체 이름을 직접 쓰는 코드는 리뷰에서 눈에 띄기 때문이다.

---

## 8. 경계를 넘는 호출의 약속: 실패는 결과값으로

경계는 "누가 누구를 참조하는가"만의 문제가 아니다. **경계를 넘는 호출이 실패할 때 어떻게 알리는가**도 경계 설계의 일부다.

주문은 하나의 트랜잭션 안에서 inventory의 재고 예약을 호출한다. inventory의 서비스도 `@Transactional`이라 **같은 트랜잭션에 참여**한다. 이때 inventory가 재고 부족을 예외로 던지면 어떻게 될까?

학습 테스트로 확인했다. 바깥 트랜잭션이 안쪽 `@Transactional` 메서드를 부르는 상황을 그대로 재현했다.

```kotlin
open class Outer(private val inner: Inner) {
    @Transactional
    open fun catchInnerException() {
        try {
            inner.reserveOrThrow()          // 안쪽 @Transactional 메서드가 예외를 던진다
        } catch (e: IllegalStateException) {
            // 잡았으니 괜찮다고 생각하기 쉽다
        }
    }
}
```

```
안쪽 트랜잭션에서 던진 예외를 바깥에서 잡아도 커밋할 수 없다     → UnexpectedRollbackException  ✓
실패를 결과값으로 돌려주면 바깥 트랜잭션은 정상적으로 커밋된다    → 예외 없음                    ✓
```

Spring은 `@Transactional` 메서드에서 런타임 예외가 빠져나가는 순간 **트랜잭션 전체를 rollback-only로 표시**한다. 바깥에서 예외를 잡고 계속 진행해도, 커밋하는 시점에 `UnexpectedRollbackException`이 터진다.

그래서 inventory의 공개 API는 재고 부족을 예외가 아니라 **결과값**으로 돌려준다.

```kotlin
// inventory/api/InventoryFacade.kt
interface InventoryFacade {
    fun reserve(items: List<StockItem>): ReserveStockResult
}

sealed interface ReserveStockResult {
    data object Reserved : ReserveStockResult
    data class Rejected(val insufficientProductIds: List<UUID>) : ReserveStockResult
}
```

재고 예약은 먼저 전부 검사한다. 하나라도 부족하면 **아무것도 바꾸지 않고** `Rejected`를 돌려준다. 실패를 어떻게 처리할지는 호출한 쪽(order)이 정한다. 지금은 order가 이 결과를 자기 모듈의 예외(`OutOfStockException`)로 바꿔서 주문을 실패시킨다. 이 결과 타입은 MSA로 가면 HTTP 응답(409와 부족한 상품 목록)으로 자연스럽게 옮겨진다 ([ADR-0010](../adr/0010-cross-module-failures-as-results.md)).

이 함정은 **모놀리식이라서** 생긴다. 네트워크를 넘으면 트랜잭션 전파 자체가 없어지니 이 문제는 사라진다. 대신 "재고는 예약됐는데 주문 저장이 실패하면?" 같은 분산 정합성 문제가 생긴다. 그건 #11에서 다룬다.

---

## 9. 아직 테스트로 못 지키는 것

1. **테이블 소유권.** "모듈은 자기 테이블만 읽고 쓴다"는 규칙([ADR-0009](../adr/0009-table-ownership.md))은 아직 테스트가 없다. 엔티티의 패키지와 `@Table` 이름을 대조하는 ArchUnit 규칙으로 만들 수 있을지 검토 중이다.
2. **이벤트 경계.** #4에서 도메인 이벤트를 도입하면 "어떤 모듈이 어떤 이벤트를 구독하는가"도 경계가 된다. Modulith의 이벤트 관련 검증과 테스트 도구는 그때 다룬다.
3. **빌드에서만 돈다.** 지금은 로컬에서 테스트를 돌려야 규칙이 동작한다. CI에서 모든 PR에 강제하는 건 #5에서 붙인다.

---

## 10. 정리

- 경계는 두 종류다. **모듈 사이**는 Spring Modulith(`verify()`)가, **모듈 안쪽 계층**은 ArchUnit이 지킨다. 두 도구는 서로 다른 위반을 잡는다.
- Modulith에서 모듈은 **패키지**다. 하위 패키지는 기본이 내부이고, `@NamedInterface`로 공개할 것만 연다. Kotlin에는 패키지 어노테이션이 없어서 `package-info.java`를 쓴다.
- **OPEN 모듈도 허용 의존 목록에 선언해야 한다.** "무엇을 공개하는가"와 "누구에게 의존해도 되는가"는 별개다.
- **규칙 테스트도 실패하는 걸 먼저 봐야 한다.** `onlyBeAccessed()`는 필드 타입으로 선언만 한 의존을 잡지 못했다. 위반 코드를 넣어보지 않았다면 모르고 지나갔을 것이다.
- **바이트코드 분석 도구는 Kotlin 값 클래스를 보지 못한다.** 값 클래스는 감싼 타입으로 지워져서 Modulith도 ArchUnit도 다른 모듈의 `MemberId` 참조를 통과시켰다. import 문을 검사하는 테스트로 메웠다.
- 경계를 넘는 **예상된 실패는 결과값으로** 돌려준다. 같은 트랜잭션 안에서 다른 모듈이 예외를 던지면, 잡아도 커밋할 수 없다.

<!-- TODO(작성자): 규칙이 위반을 못 잡았던 걸 알게 됐을 때의 생각이나, 규칙 테스트에 대해 새로 알게 된 점 -->

다음 편(#4)에서는 모듈 간 **비동기 협력**을 다룬다. 도메인 이벤트, Spring Modulith의 이벤트 발행 저장소(사실상 트랜잭셔널 아웃박스), 가짜 PG를 붙인 결제, 그리고 "외부 호출은 DB 트랜잭션 밖에서"를 구현한다.

---

## 참고 자료

- [Spring Modulith 레퍼런스 문서](https://docs.spring.io/spring-modulith/reference/)
- [ArchUnit 사용자 가이드](https://www.archunit.org/userguide/html/000_Index.html)
- [Konsist](https://docs.konsist.lemonappdev.com/): Kotlin 소스를 구문 분석하는 아키텍처 검사 도구
- [Kotlin inline value classes](https://kotlinlang.org/docs/inline-classes.html): 값 클래스가 어떻게 컴파일되는가
- [spring-projects/spring-modulith](https://github.com/spring-projects/spring-modulith)
- 설계 결정: [ADR-0003 모듈 내부 구조와 검증 규칙](../adr/0003-module-internal-structure.md), [ADR-0008 공유 커널](../adr/0008-shared-kernel.md), [ADR-0010 모듈 간 실패는 결과값으로](../adr/0010-cross-module-failures-as-results.md)
