# ADR-0004. 도메인 모델과 JPA 엔티티를 분리한다

- 상태: 승인
- 날짜: 2026-09-24

## 맥락

도메인 클래스에 `@Entity`를 붙이면 코드가 가장 짧다. 대신 도메인이 영속성 기술에 묶인다.

- JPA 요구사항(기본 생성자, open 클래스, 가변 필드, 프록시)이 도메인 설계를 제약한다.
- 다른 모듈의 엔티티를 `@ManyToOne`으로 참조하는 코드가 쉽게 생긴다. 그러면 모듈 경계를 넘는 FK와 JOIN이 따라온다.
- 지연 로딩, dirty checking처럼 눈에 보이지 않는 동작이 도메인 로직에 섞인다.

## 결정

- `domain` 패키지는 순수 Kotlin으로 작성한다. `LayerDependencyTests`가 이를 강제한다.
- JPA 엔티티는 `infrastructure/persistence`에 따로 두고, 영속성 어댑터에서 도메인 모델로 변환한다.
- 다른 애그리거트는 객체 참조가 아니라 **ID로만 참조**한다 (예: `Order`는 `Member`가 아니라 `MemberId`를 가진다).

## 결과

- 도메인 로직을 DB 없이 단위 테스트할 수 있다.
- 모듈을 떼어낼 때 도메인 코드가 영속성 기술과 무관하게 이동한다.
- ID 참조 덕분에 모듈 간 FK가 애초에 생기지 않는다. DB를 분리할 때 끊어낼 연결이 줄어든다.
- 비용:
  - 매퍼 코드가 생긴다.
  - dirty checking에 기대지 않고 명시적으로 `save`해야 한다.
  - 변환 비용이 생긴다. 성능 편(P4)에서 측정한다.
- 다시 볼 것: 조회 전용 API는 도메인 모델을 거치지 않고 조회 모델(프로젝션)로 바로 읽을 수 있다. P1에서 조회 경로를 설계할 때 정한다.

## P1 구현에서 구체화한 것

**저장 방식.** 영속성 어댑터의 `save(domain)`은 다음처럼 동작한다.

```kotlin
val entity = jpaRepository.findByIdOrNull(domain.id)   // 같은 트랜잭션에서 이미 불러왔다면 1차 캐시에서 꺼낸다
if (entity == null) jpaRepository.save(Entity.from(domain))   // 새 애그리거트 → INSERT
else entity.update(domain)                                     // 기존 애그리거트 → 커밋 시 변경 감지로 UPDATE
```

- 서비스 입장에서는 항상 명시적으로 `save`를 부른다. 변경 감지는 어댑터 안에 숨는다.
- 엔티티를 새로 만들지 않고 기존 엔티티에 상태를 옮겨 담기 때문에 `@Version` 값이 유지되고 **낙관적 락이 그대로 동작**한다. 도메인 모델은 버전을 몰라도 된다.
- 모든 엔티티에 `@Version var version: Long? = null`을 둔다. Spring Data는 이 값이 null이면 새 엔티티로 보고 `persist`한다. ID를 애플리케이션이 만들기 때문에(ADR-0007) 이게 없으면 `save`가 불필요한 SELECT 후 `merge`를 한다.

**조회 경로.** 도메인 저장소(`ProductRepository`)는 애그리거트를 저장하고 ID로 불러오는 일만 한다. 페이지 조회처럼 화면이 요구하는 조회는 `application/port/out`의 조회 포트(`ProductListPort`, `OrderListPort`)로 분리했다.
- 처음에는 `ProductRepository`에 `findPage(PageQuery)`를 두려고 했다. 그런데 `PageQuery`는 `shared.application`에 있어서, 그대로 두면 도메인이 애플리케이션 계층에 의존하게 된다("domain은 application을 모른다" 규칙 위반).
- 목록 조회는 도메인 규칙이 아니라 화면의 요구라는 점에서, 의존 규칙이 설계를 바로잡아 준 셈이다. 지금은 조회 포트도 도메인 객체를 돌려주지만, P4에서 조회 전용 모델로 바꾸더라도 도메인은 영향을 받지 않는다.
