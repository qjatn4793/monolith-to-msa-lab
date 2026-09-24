# P1 코드 따라가기: 주문 요청 하나가 거치는 길

`POST /orders` 요청 하나를 따라가면서 헥사고날 구조의 각 계층이 무슨 일을 하는지 본다.
경로는 전부 `monolith/src/main/kotlin/com/beomsoo/shop/` 기준이다.

## 전체 그림

```
HTTP POST /orders
  │
  ▼
[order] infrastructure/web/OrderController                  인바운드 어댑터: HTTP → 커맨드
  │  PlaceOrderUseCase (port/in)
  ▼
[order] application/service/OrderCommandService            유스케이스: 흐름 조율, 트랜잭션 경계
  │
  ├─▶ LoadOrdererPort (port/out)
  │     └─ infrastructure/adapter/MemberAdapter ──────▶ [member] api/MemberFacade
  │                                                         └─ infrastructure/facade/MemberFacadeAdapter
  │                                                              └─ GetMemberQuery → MemberQueryService → MemberRepository
  │
  ├─▶ LoadProductPort (port/out)
  │     └─ infrastructure/adapter/CatalogAdapter ─────▶ [catalog] api/CatalogFacade
  │
  ├─▶ Order.place(...)                                      도메인: 주문 규칙 검사
  │
  ├─▶ StockPort (port/out)
  │     └─ infrastructure/adapter/InventoryAdapter ───▶ [inventory] api/InventoryFacade
  │                                                         └─ StockCommandService.reserve → Stock.reserve
  │
  └─▶ OrderRepository (domain)
        └─ infrastructure/persistence/OrderPersistenceAdapter → OrderJpaEntity → MySQL
```

## 1. 컨트롤러: HTTP를 유스케이스 호출로 바꾼다

`order/infrastructure/web/OrderController.kt`

```kotlin
@PostMapping
fun place(@Valid @RequestBody request: PlaceOrderRequest): ResponseEntity<IdResponse> {
    val id = placeOrderUseCase.place(PlaceOrderCommand(...))
    return ResponseEntity.created(URI.create("/orders/$id")).body(IdResponse(id.value))
}
```

- 컨트롤러는 `OrderCommandService`(구현)가 아니라 `PlaceOrderUseCase`(인터페이스)에 의존한다.
- `@Valid`로 형식만 검증한다(빈 목록, 음수 수량). "수량 999개 이하" 같은 비즈니스 규칙은 도메인이 검증한다.
- 요청 DTO(`PlaceOrderRequest`)를 커맨드(`PlaceOrderCommand`)로 바꾼다. 웹의 형식이 유스케이스 안으로 새어 들어가지 않게 하기 위해서다.

## 2. 서비스: 흐름을 조율한다

`order/application/service/OrderCommandService.kt`

```kotlin
override fun place(command: PlaceOrderCommand): OrderId {
    val orderer = loadOrderer(command)                  // 1. 주문자 확인 (member)
    val lines = createLines(command.items)              // 2. 상품 스냅샷 (catalog)
    val order = Order.place(orderer, lines, clock.instant())  // 3. 주문 생성 (도메인 규칙)
    when (val reservation = stockPort.reserve(order.lines)) { // 4. 재고 예약 (inventory)
        ...
    }
    orderRepository.save(order)                         // 5. 저장
    return order.id
}
```

- 이 클래스의 import를 보면 `member`, `catalog`, `inventory` 패키지가 **하나도 없다.** 전부 order가 정의한 포트(`LoadOrdererPort`, `LoadProductPort`, `StockPort`)다.
- `@Transactional`이 붙어 있어서 이 메서드 전체가 DB 트랜잭션 하나다. 재고 예약까지 같은 트랜잭션이라 중간에 실패하면 전부 롤백된다. **모놀리식이라서 가능한 일**이고, MSA로 가면 이 전제가 깨진다.
- 현재 시각은 `Clock`에서 받아 도메인에 넘긴다. 도메인이 `Instant.now()`를 직접 부르지 않으니 테스트에서 시각을 고정할 수 있다.

## 3. 아웃바운드 포트와 어댑터: 다른 모듈을 부른다

포트는 order의 언어로 정의한다. `order/application/port/out/LoadOrdererPort.kt`

```kotlin
interface LoadOrdererPort {
    fun loadOrderer(memberId: UUID): Orderer?
}
data class Orderer(val memberId: UUID, val active: Boolean)
```

주문에 필요한 건 "주문할 수 있는 회원인가"뿐이라, 회원의 이메일이나 이름은 가져오지 않는다.

어댑터가 포트를 구현하면서 member의 공개 API를 부른다. `order/infrastructure/adapter/MemberAdapter.kt`

```kotlin
override fun loadOrderer(memberId: UUID): Orderer? =
    memberFacade.findMember(memberId)?.let { Orderer(memberId = it.id, active = it.active) }
```

- order 모듈 전체에서 `member` 패키지를 import 하는 파일은 **이것 하나**다.
- 그마저도 `member.api`만 import 한다. `member.domain.Member`를 import 하면 `ModularityTests`가 실패한다.
- member를 서비스로 떼어내는 날에는 이 파일만 HTTP 호출로 바꾸면 된다.

## 4. 다른 모듈의 입구: 파사드

`member/api/MemberFacade.kt`는 인터페이스와 DTO뿐이다. 구현은 `member/infrastructure/facade/MemberFacadeAdapter.kt`에 있다.

```kotlin
override fun findMember(memberId: UUID): MemberInfo? =
    getMemberQuery.findMember(MemberId(memberId))?.let {
        MemberInfo(id = it.id.value, email = it.email.value, name = it.name, active = it.isActive)
    }
```

- 파사드 구현도 컨트롤러와 똑같이 인바운드 포트(`GetMemberQuery`)를 호출한다. member 입장에서는 HTTP 클라이언트든 order 모듈이든 똑같이 "밖에서 온 사용자"다.
- 도메인 객체(`Member`)가 아니라 `MemberInfo`(UUID, String, Boolean)를 돌려준다. 공개 계약에 도메인이 새어 나가면 member가 도메인을 바꿀 때마다 order가 깨진다.

## 5. 도메인: 규칙은 여기에 있다

`order/domain/Order.kt`

```kotlin
companion object {
    fun place(memberId: UUID, lines: List<OrderLine>, now: Instant): Order =
        Order(OrderId.new(), memberId, lines, OrderStatus.PENDING, now)

    private fun validateLines(lines: List<OrderLine>): List<OrderLine> {
        if (lines.isEmpty()) throw InvalidInputException("주문 상품이 없습니다.")
        ...
    }
}
```

- Spring도 JPA도 import 하지 않는다. `LayerDependencyTests`가 이를 강제한다.
- 주문자를 `Member` 객체가 아니라 `memberId: UUID`로 들고 있다. 그래서 주문을 불러올 때 회원 테이블을 JOIN 할 일이 없다.
- `OrderLine`에 상품명과 가격을 **복사**해 둔다. 나중에 catalog에서 가격이 바뀌어도 이미 한 주문은 영향을 받지 않는다 (`OrderFlowIntegrationTest`에서 확인).

## 6. 재고 예약: 실패를 결과값으로 돌려준다

`inventory/application/service/StockCommandService.kt`

```kotlin
val shortages = requested.mapNotNull { ... }
if (shortages.isNotEmpty()) return ReservationResult.Rejected(shortages)   // 아무것도 바꾸지 않고 돌려준다
requested.forEach { (productId, quantity) -> stocks.getValue(productId).reserve(quantity) }
```

재고 부족을 예외로 던지지 않는 이유는 [ADR-0010](../adr/0010-cross-module-failures-as-results.md)과 `RollbackOnlyLearningTest`에 정리했다. 한 줄로 요약하면, 같은 트랜잭션 안에서 다른 모듈의 `@Transactional` 메서드가 예외를 던지면 호출한 쪽이 그 예외를 잡아도 커밋할 수 없기 때문이다.

## 7. 저장: 도메인 모델 ↔ JPA 엔티티

`order/infrastructure/persistence/OrderPersistenceAdapter.kt`

```kotlin
override fun save(order: Order) {
    val entity = jpaRepository.findByIdOrNull(order.id.value)
    if (entity == null) jpaRepository.save(OrderJpaEntity.from(order))
    else entity.update(order)
}
```

- 도메인 모델(`Order`)과 JPA 엔티티(`OrderJpaEntity`)는 별개의 클래스다 ([ADR-0004](../adr/0004-separate-domain-and-jpa-model.md)).
- 이미 있는 주문이면 엔티티에 상태를 옮겨 담아서 `@Version`(낙관적 락)이 그대로 동작한다.

## 테스트가 어떻게 나뉘어 있나

| 테스트 | 범위 | Spring | DB | 속도 |
|---|---|---|---|---|
| `*/domain/*Test` | 도메인 규칙 | ✗ | ✗ | 매우 빠름 |
| `OrderCommandServiceTest` | 유스케이스 흐름. 포트를 가짜 구현으로 바꿔 끼움 | ✗ | ✗ | 매우 빠름 |
| `OrderFlowIntegrationTest` | HTTP → 모든 계층 → MySQL | ○ | ○ (Testcontainers) | 느림 |
| `ModularityTests`, `LayerDependencyTests` | 구조 규칙 | ✗ | ✗ | 빠름 |
| `RollbackOnlyLearningTest` | Spring 트랜잭션 동작 학습 | ○ | ○ | 느림 |

`OrderCommandServiceTest`는 member, catalog, inventory 모듈이 없어도 돈다. 포트를 가짜로 바꿔 끼울 수 있다는 게 헥사고날 구조의 실질적인 이득이다.

## 직접 해볼 것

코드를 읽는 것보다 직접 바꿔보는 게 빨리 이해된다.

1. **규칙을 깨보기**: `OrderCommandService`에서 `com.beomsoo.shop.member.domain.MemberRepository`를 직접 주입받아 보고 `./gradlew :monolith:test --tests '*ModularityTests*'`를 돌려본다. 어떤 메시지가 나오는가?
2. **도메인에 Spring 넣어보기**: `Order` 클래스에 `@Component`를 붙이고 `LayerDependencyTests`를 돌려본다.
3. **유스케이스 추가하기**: "회원 이메일 변경"을 member 모듈에 추가해본다. 도메인 메서드 → 포트 → 서비스 → 컨트롤러 → 테스트 순서로. 이메일 중복 검사는 어디에 둬야 할까?
4. **어댑터 바꿔보기**: `MemberAdapter`가 `MemberFacade` 대신 항상 `Orderer(memberId, active = true)`를 돌려주도록 바꿔본다. 주문 서비스 코드를 하나도 안 고치고 동작이 바뀌는 것을 확인한다. MSA 전환 때 하는 일이 바로 이것이다.
