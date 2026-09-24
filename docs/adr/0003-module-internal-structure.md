# ADR-0003. 모듈 내부는 api / domain / application / infrastructure로 나눈다

- 상태: 승인
- 날짜: 2026-09-24

## 맥락

모듈 사이의 경계(ADR-0002)와 별개로, 모듈 **안쪽**의 구조도 정해야 한다. 참고한 구조는 세 가지다.

- [buckpal](https://github.com/thombergs/buckpal): 포트와 어댑터를 명확하게 나눈다. 다만 애플리케이션 전체가 헥사곤 하나다.
- [ddd-by-examples/library](https://github.com/ddd-by-examples/library): 바운디드 컨텍스트마다 `model / application / infrastructure`로 나눈다.
- [kgrzybek/modular-monolith-with-ddd](https://github.com/kgrzybek/modular-monolith-with-ddd): 공개 계약(`Contracts`, `IntegrationEvents`)을 내부 구현과 물리적으로 분리한다.

## 결정

```
<module>/
├── api/             [공개] 다른 모듈이 참조할 수 있는 유일한 패키지 (@NamedInterface("api"))
│                    파사드, 공개 DTO, 통합 이벤트
├── domain/          [내부] 애그리거트, 값 객체, 도메인 이벤트, 저장소 인터페이스
│                    순수 Kotlin. Spring과 JPA에 의존하지 않는다
├── application/     [내부] 유스케이스와 트랜잭션 경계
│   ├── port/in/     유스케이스 인터페이스
│   ├── port/out/    외부로 나가는 포트 (다른 모듈, 외부 시스템)
│   └── service/     유스케이스 구현
└── infrastructure/  [내부] 어댑터
    ├── web/         인바운드: 컨트롤러
    ├── facade/      인바운드: 자기 모듈 api 인터페이스의 구현 (P1에서 추가)
    ├── persistence/ 아웃바운드: JPA 엔티티, 매퍼, 저장소 구현
    └── adapter/     아웃바운드: 다른 모듈의 api 호출, 외부 시스템 클라이언트
```

컨트롤러는 인바운드 어댑터로 보고 `infrastructure/web`에 둔다.

`api`에는 인터페이스(`MemberFacade`)만 두고, 구현(`MemberFacadeAdapter`)은 `infrastructure/facade`에 둔다. 다른 모듈도 웹 클라이언트처럼 이 모듈을 **사용하는 쪽**이므로, 파사드 구현은 컨트롤러와 같은 인바운드 어댑터다. 컨트롤러처럼 인바운드 포트(유스케이스)를 호출한다.

```
HTTP 요청   ─▶ web/MemberController        ─┐
                                              ├─▶ port/in (유스케이스) ─▶ service ─▶ domain
order 모듈  ─▶ facade/MemberFacadeAdapter  ─┘
               (api/MemberFacade의 구현)
```

### 의존 규칙

```
infrastructure ──▶ application ──▶ domain
       └───────────────────────────▶
```

| 규칙 | 검증 |
|---|---|
| 다른 모듈은 `api`만 참조한다 | `ModularityTests` (Spring Modulith) |
| 허용된 모듈에만 의존한다 (ADR-0002) | `ModularityTests` |
| `domain`은 `application`, `infrastructure`를 모른다 | `LayerDependencyTests` (ArchUnit) |
| `domain`은 Spring, JPA에 의존하지 않는다 | `LayerDependencyTests` |
| `application`은 `infrastructure`를 모른다 | `LayerDependencyTests` |
| `api`는 `domain`, `application`, `infrastructure`를 노출하지 않는다 | `LayerDependencyTests` |

### 검증 규칙이 실제로 동작하는지 확인

P0에서 일부러 위반 코드를 넣고 테스트를 돌려봤다.

`order` 모듈의 인프라 코드가 `inventory`의 내부 도메인 클래스를 참조한 경우:
```
Module 'order' depends on non-exposed type com.beomsoo.shop.inventory.domain.Stock within module 'inventory'!
Field <com.beomsoo.shop.order.infrastructure.Violation.stock> has type <com.beomsoo.shop.inventory.domain.Stock> in (Violation.kt:0)
```

도메인 클래스에 Spring 어노테이션을 붙인 경우:
```
Rule 'no classes that reside in a package '..domain..' should depend on classes that reside in any package
['org.springframework..', 'jakarta.persistence..']' was violated (1 times):
Class <com.beomsoo.shop.payment.domain.SpringInDomain> is annotated with <org.springframework.stereotype.Component> in (SpringInDomain.kt:0)
```

## 결과

- 서비스를 떼어낼 때 `domain`과 `application`은 그대로 옮기고, `infrastructure/adapter`만 교체하면 되는 구조가 된다. 정말 그런지는 전환 단계에서 확인한다.
- 비용: 기능 하나를 추가할 때 만드는 파일이 많다 (포트, 서비스, 어댑터, 매퍼). 단순 CRUD 모듈에서는 과해 보일 수 있다.
- 다시 볼 것: 단순한 모듈(예: notification)에 이 구조를 전부 적용할지, 필요한 레이어만 둘지는 구현하면서 판단한다.
- Kotlin에는 패키지 어노테이션이 없어서, 모듈과 공개 계약 선언은 `src/main/java`의 `package-info.java`에 둔다.
