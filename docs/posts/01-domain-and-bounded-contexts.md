# [모놀리식에서 MSA로 #1] 모듈을 어디서 나눌까: 도메인 분석과 바운디드 컨텍스트

> 시리즈 코드는 전부 [monolith-to-msa-lab 저장소](https://github.com/qjatn4793/monolith-to-msa-lab)에 있다.
> 이 글에서 다루는 설계 결정은 [ADR-0001](../adr/0001-modular-monolith-first.md), [ADR-0002](../adr/0002-bounded-contexts.md)에 정리해 두었다.

[지난 글(#0)](00-how-companies-design-monoliths.md)에서 정리한 결론은 이랬다. MSA로 가기 전에 **경계가 단단한 모듈러 모놀리식**을 먼저 만든다. 떼어내는 단위는 기술 축(API, 도메인, 인프라)이 아니라 **도메인 축**(주문, 결제, 재고)이다.

그렇다면 바로 다음 질문이 생긴다. **도메인 축의 경계는 어디에 그어야 하는가?**

모듈을 잘못 나누면 모놀리식 안에서는 그럭저럭 돌아간다. 같은 프로세스, 같은 DB, 같은 트랜잭션이 경계의 실수를 덮어주기 때문이다. 문제는 그 경계를 따라 서비스를 떼어내는 순간 드러난다. 잘못 그은 경계는 그대로 네트워크 호출이 되고, 분산 트랜잭션이 되고, 장애 전파 경로가 된다.

이번 글은 이 시리즈의 이커머스 서비스를 **여섯 개 모듈로 나눈 과정과 이유**를 다룬다. 특히 "상품"을 두 개의 모듈로 쪼갠 결정을 중심으로 이야기하려고 한다.

<!-- TODO(작성자): 모놀리식만 운영해오면서 "이건 어디에 둬야 하지?" 하고 고민했던 경험이 있다면 한 문단 추가 -->

---

## 0. 만들 서비스: 이커머스 주문

도메인으로 이커머스 주문을 골랐다. 이유는 두 가지다.

1. **설명이 필요 없다.** 회원이 상품을 골라 주문하고, 재고가 빠지고, 결제하고, 알림을 받는다. 독자가 도메인을 이해하는 데 에너지를 쓰지 않아도 된다.
2. **MSA 전환에서 다루고 싶은 문제가 전부 나온다.** 주문, 재고, 결제를 하나로 묶는 트랜잭션, 재고 차감의 동시성, 외부 PG 연동, 여러 모듈의 데이터를 조합하는 조회까지.

기능 범위는 이렇다. 인증과 배송은 이후 편으로 미뤘다.

| 기능 | 설명 |
|---|---|
| 회원 | 가입, 조회, 이름 변경, 탈퇴 |
| 상품 | 등록, 조회, 가격 변경, 판매 중지와 재개 |
| 재고 | 입고, 조회, 주문 시 예약, 취소 시 해제 |
| 주문 | 주문, 조회, 취소 |
| 결제 | 외부 PG 연동 (#4에서 구현) |
| 알림 | 주문 결과 알림 (#4에서 구현) |

---

## 1. 바운디드 컨텍스트: 같은 단어가 다른 뜻이 되는 곳

DDD에서 모듈 경계를 정하는 핵심 개념은 **바운디드 컨텍스트(Bounded Context)**다. Martin Fowler는 [BoundedContext](https://martinfowler.com/bliki/BoundedContext.html)에서 이렇게 설명한다. 큰 도메인 전체에 하나의 통일된 모델을 만드는 건 현실적이지 않다. 그래서 도메인을 여러 컨텍스트로 나누고, **각 컨텍스트 안에서만 일관된 모델**을 만든다.

설명만 보면 추상적인데, 이 서비스에서 **"상품"이라는 단어**를 따라가 보면 바로 보인다. 같은 "상품"인데 누가 말하느냐에 따라 관심사가 완전히 다르다.

**상품 담당자에게 상품은** 이름, 가격, 설명, 판매 여부다.

```kotlin
// catalog/domain/Product.kt
class Product(
    val id: ProductId,
    name: String,
    price: Money,
    description: String?,
    status: ProductStatus,      // ON_SALE, STOPPED
    val registeredAt: Instant,
)
```

**창고 담당자에게 상품은** 몇 개가 남았고 몇 개가 주문에 잡혀 있는지다. 이름이나 가격에는 관심이 없다.

```kotlin
// inventory/domain/Stock.kt
class Stock(
    val productId: UUID,
    available: Int,   // 지금 주문할 수 있는 수량
    reserved: Int,    // 주문이 잡아둔 수량
)
```

**주문 입장에서 상품은** "주문하던 그 순간의" 이름과 가격이다. 나중에 가격이 바뀌어도 이미 한 주문의 금액은 바뀌면 안 된다.

```kotlin
// order/domain/OrderLine.kt
data class OrderLine(
    val productId: UUID,
    val productName: String,   // 주문 시점의 상품명 (스냅샷)
    val unitPrice: Money,      // 주문 시점의 가격 (스냅샷)
    val quantity: Int,
)
```

만약 이 셋을 하나의 `Product` 클래스로 만들었다면 어떻게 됐을까. 이름, 가격, 설명, 판매 상태, 가용 재고, 예약 재고를 전부 가진 클래스가 된다. 가격을 바꾸는 코드와 재고를 빼는 코드가 같은 객체, 같은 테이블 행을 건드린다. 주문 금액은 상품 테이블을 참조하게 되고, 가격을 바꾸면 과거 주문 금액까지 흔들린다.

> 같은 단어가 다른 뜻으로 쓰이기 시작하는 곳이 경계를 그을 후보다.

---

## 2. 경계를 나눈 두 가지 기준

실제로 경계를 그을 때는 두 가지 질문을 던졌다.

**질문 1. 언어가 달라지는가?**
앞에서 본 "상품"처럼 같은 단어가 다른 의미로 쓰이면 다른 컨텍스트다. 회원도 마찬가지다. member 모듈에게 회원은 이메일, 이름, 상태를 가진 대상이다. 반면 주문에게 회원은 "주문할 수 있는 사람인가"라는 질문 하나로 충분하다. 그래서 order 모듈은 회원을 `Member`가 아니라 `Orderer`라는 자기 언어로 부른다.

```kotlin
// order/application/port/out/LoadOrdererPort.kt
data class Orderer(
    val memberId: UUID,
    val active: Boolean,
)
```

**질문 2. 변경 이유와 부하 특성이 다른가?**
언어가 같아 보여도 바뀌는 이유가 다르거나, 트래픽 패턴이 다르면 나누는 게 낫다. 함께 바뀌는 것은 묶고, 따로 바뀌는 것은 나눈다. 다음 절의 catalog와 inventory 분리가 이 기준에서 나왔다.

---

## 3. 결과: 여섯 개의 모듈

| 모듈 | 책임 | 의존할 수 있는 모듈 |
|---|---|---|
| `member` | 가입, 인증, 회원 정보 | 없음 |
| `catalog` | 상품 정보 (이름, 가격, 설명, 판매 상태) | 없음 |
| `inventory` | 재고 수량, 예약, 해제 | 없음 |
| `payment` | 결제, 외부 PG 연동 | 없음 |
| `order` | 주문 생성과 상태 흐름. 다른 모듈을 조율한다 | member, catalog, inventory, payment |
| `notification` | 알림 발송. 이벤트에 반응만 한다 | order, member |

(모든 모듈은 공통 값 객체를 담은 `shared`에도 의존한다. 공유 커널은 #3에서 따로 다룬다.)

의존 방향을 그림으로 보면 이렇다.

```
notification ──▶ order ──┬──▶ member
     │                   ├──▶ catalog
     │                   ├──▶ inventory
     │                   └──▶ payment
     └──────────────────────▶ member
```

이 설계는 문서로만 남기지 않고 코드에 선언했다. Spring Modulith의 `@ApplicationModule`로 각 모듈이 **어떤 모듈에 의존해도 되는지**를 적어둔다.

```java
// order/package-info.java
@ApplicationModule(
        displayName = "Order",
        allowedDependencies = {"shared", "member :: api", "catalog :: api", "inventory :: api", "payment :: api"}
)
package com.beomsoo.shop.order;
```

`member :: api`는 "member 모듈의 `api` 패키지만"이라는 뜻이다. 선언하지 않은 모듈을 참조하거나 `api`가 아닌 내부 패키지를 참조하면 테스트가 실패한다. 이 부분은 #3에서 자세히 다룬다.

P1 구현을 마친 뒤 Modulith가 코드를 분석해서 그린 의존 관계는 다음과 같다. 설계한 그대로다.

```
Order     ── uses ──▶ Catalog
Order     ── uses ──▶ Inventory
Order     ── uses ──▶ Member
Order     ── depends on ──▶ Shared Kernel
Inventory ── depends on ──▶ Shared Kernel
Catalog   ── depends on ──▶ Shared Kernel
Member    ── depends on ──▶ Shared Kernel
```

payment와 notification은 모듈 선언만 해두었고, 구현은 이벤트와 함께 #4에서 한다.

---

## 4. 가장 고민한 결정: 상품을 catalog와 inventory로 나눈 이유

모듈 목록에서 가장 논쟁적인 부분이 여기다. 상품 정보(catalog)와 재고(inventory)를 **다른 모듈**로 나눴다. 재고를 상품의 속성으로 보고 `Product`에 `stockQuantity` 필드 하나를 두는 설계가 훨씬 흔하고 단순하다.

그래도 나눈 이유는 셋이다.

### 이유 1. 쓰기 경합

재고는 주문이 들어올 때마다 바뀐다. 인기 상품이라면 초당 수십, 수백 번 같은 행을 갱신한다. 반면 상품 정보는 운영자가 가끔 가격을 바꾸는 정도다.

둘이 같은 행에 있으면, **가격 변경과 재고 차감이 같은 행을 두고 경합**한다. 이 프로젝트는 모든 엔티티에 낙관적 락(`@Version`)을 걸어두었다. 그래서 운영자가 가격을 바꾸는 순간 동시에 들어온 주문이 버전 충돌로 실패하거나, 반대로 주문 폭주 때문에 가격 변경이 계속 실패할 수 있다. 행을 나누면 이런 간섭이 원천적으로 사라진다.

### 이유 2. 바뀌는 이유가 다르다

| | catalog | inventory |
|---|---|---|
| 누가 바꾸나 | 상품 운영자 | 주문, 입고 |
| 얼마나 자주 | 가끔 | 주문마다 |
| 무엇이 중요한가 | 정확한 정보, 검색, 노출 | 음수가 되지 않는 수량, 동시성 |

### 이유 3. 확장 방식이 다르다

catalog는 **읽기 위주**라 캐시가 잘 먹는다. inventory는 **쓰기 위주**라 캐시보다 동시성 제어가 핵심이다. 나중에 성능 편(#6~7)에서 재고 동시성 전략(낙관적 락, 비관적 락, 원자적 UPDATE 등)을 비교할 예정인데, 재고가 분리되어 있어야 catalog와 무관하게 실험할 수 있다.

### 치르는 비용

공짜는 아니다. 나누면서 생긴 비용도 정직하게 적어둔다.

- **상품을 등록해도 재고가 자동으로 생기지 않는다.** 지금은 상품 등록(`POST /products`) 후 입고(`POST /stocks/{productId}/receive`)를 따로 호출해야 한다.
- **입고할 때 상품이 실제로 있는지 확인하지 않는다.** inventory는 catalog에 의존하지 않도록 설계했기 때문이다. 없는 상품 ID로 입고해도 재고가 생긴다. #4에서 "상품이 등록됐다"는 이벤트를 도입하면 이 부분을 다시 볼 예정이다.
- MSA로 전환하면 "상품 등록과 재고 생성"은 서로 다른 서비스에 걸친 작업이 된다. 즉 **분산 정합성 문제**가 하나 늘어난다.

---

## 5. 모듈 사이의 관계는 ID로만

경계를 나눈 다음에는 **경계를 넘는 참조를 어떻게 할지** 정해야 한다. 원칙은 하나다. **다른 모듈의 객체가 아니라 ID만 들고 있는다.**

주문은 회원을 `Member` 객체가 아니라 `memberId: UUID`로 참조한다.

```kotlin
// order/domain/Order.kt
class Order(
    val id: OrderId,
    val memberId: UUID,        // Member 객체가 아니라 ID
    lines: List<OrderLine>,
    status: OrderStatus,
    val orderedAt: Instant,
)
```

DB에서도 모듈 경계를 넘는 외래 키(FK)를 걸지 않는다. 같은 모듈 안에서는 건다.

```sql
-- order 모듈의 마이그레이션 (db/migration/order/V4__create_orders.sql)
-- member_id, product_id는 다른 모듈의 ID지만 모듈 경계를 넘는 FK는 걸지 않는다.
-- order_line → orders 처럼 같은 모듈 안의 FK는 건다.
create table orders (
    id        binary(16) not null,
    member_id binary(16) not null,     -- FK 없음
    ...
);
create table order_line (
    ...
    constraint fk_order_line_order foreign key (order_id) references orders (id)
);
```

이렇게 해두면 좋은 점과 나쁜 점이 분명하다.

- **좋은 점**: 주문을 불러올 때 회원 테이블과 JOIN 할 일이 없다. 나중에 DB를 나눌 때 끊어낼 FK가 애초에 없다.
- **나쁜 점**: "없는 회원 ID로 주문하기"를 DB가 막아주지 않는다. 대신 주문 서비스가 member 모듈의 공개 API로 확인한다. 무결성을 DB가 아니라 애플리케이션이 책임지게 된 것이다.

### 가격 스냅샷은 정말 동작하는가

1절에서 "주문은 주문 시점의 가격을 복사해 둔다"고 했다. 이게 실제로 동작하는지는 통합 테스트로 확인했다. 주문한 뒤 상품 가격을 바꾸고, 주문을 다시 조회한다.

```kotlin
@Test
fun `상품 가격이 바뀌어도 이미 한 주문의 금액은 그대로다`() {
    val memberId = joinMember()
    val product = registerProduct("모니터", 300_000, stock = 3)
    val orderId = post("/orders", orderJson(memberId, product to 1)).json<String>("$.id")

    mvc.patch().uri("/products/$product/price")
        .contentType(MediaType.APPLICATION_JSON).content("""{"price": 250000}""").exchange()

    val detail = mvc.get().uri("/orders/$orderId").exchange()
    assertThat(detail.json<Int>("$.lines[0].unitPrice")).isEqualTo(300_000)   // 통과
}
```

주문이 상품 테이블을 참조하는 구조였다면 이 테스트는 250,000을 돌려줬을 것이다.

---

## 6. 누가 누구를 아는가

의존 방향도 의도해서 정했다. 규칙은 **"조율하는 쪽이 조율당하는 쪽을 안다. 반대는 모른다."**

- 주문을 만들려면 회원, 상품, 재고를 확인해야 한다. 그래서 **order가 member, catalog, inventory를 안다.**
- 반대로 재고는 누가 자기를 예약하는지 모른다. inventory 입장에서 주문은 "이 상품을 몇 개 잡아달라"는 요청일 뿐이다.
- notification은 **들어오는 의존이 하나도 없는** 가장자리 모듈이다. 아무도 notification을 부르지 않는다. notification은 주문 이벤트를 구독하기만 한다.

마지막 특징 때문에 notification은 나중에 **첫 번째로 떼어낼 후보**가 된다. #0에서 정리했던 추출 기준("모놀리식에 의존하지 않는 가장자리의 단순한 기능부터")에 정확히 들어맞는다. 이 판단이 실제로 맞았는지는 #8에서 다시 확인한다.

의존이 한 방향으로만 흐르니 **순환 의존이 없다.** Modulith의 `verify()`는 모듈 사이에 순환이 생기면 테스트를 실패시킨다.

---

## 7. 아직 모르는 것

경계를 그은 지금 시점에 확신할 수 없는 부분도 적어둔다. 나중에 틀렸다면 무엇이 틀렸는지 비교하기 위해서다.

1. **order가 너무 많이 아는 것은 아닌가.** order는 모듈 네 개에 의존한다. 주문 흐름이 복잡해지면 order가 "모든 걸 조율하는 신(God) 모듈"이 될 수 있다. #4에서 이벤트를 도입하면 일부 의존을 끊을 수 있을지 본다.
2. **payment는 order에 속해야 하지 않나.** 결제는 주문 없이 존재하지 않는다. 하지만 PG 연동, 환불, 정산처럼 독자적으로 커지는 영역이라 분리했다. 이 판단은 #4에서 결제를 구현하면서 다시 본다.
3. **경계가 틀렸다면 얼마나 쉽게 고칠 수 있나.** 모놀리식 안에서는 패키지를 옮기고 테스트를 고치면 된다. 이게 모놀리식 먼저 가는 가장 큰 이유다.

---

## 8. 정리

- 모듈 경계는 **같은 단어가 다른 뜻이 되는 곳**, 그리고 **바뀌는 이유와 부하 특성이 달라지는 곳**에 긋는다.
- 이 서비스는 member, catalog, inventory, payment, order, notification **여섯 개 모듈**로 나눴다. 허용 의존은 코드(`@ApplicationModule`)에 선언하고 테스트로 검증한다.
- "상품"은 catalog(정보)와 inventory(재고)로 나눴다. 쓰기 경합, 변경 이유, 확장 방식이 다르기 때문이다. 대가로 상품 등록과 재고 생성이 분리되었다.
- 모듈 사이는 **ID로만** 참조하고, 경계를 넘는 FK는 걸지 않는다. 주문은 상품의 이름과 가격을 **스냅샷**으로 복사해 둔다.

<!-- TODO(작성자): 도메인을 나누면서 새로 알게 된 점이나 의외였던 점을 한두 줄 -->

다음 편(#2)에서는 이렇게 나눈 모듈 **안쪽**을 다룬다. 애그리거트를 어떻게 설계했는지, 헥사고날 구조에서 포트와 어댑터가 실제로 어떤 코드가 되는지, 그리고 도메인 모델과 JPA 엔티티를 왜 굳이 분리했는지를 이야기할 예정이다.

---

## 참고 자료

- Martin Fowler, [BoundedContext](https://martinfowler.com/bliki/BoundedContext.html)
- Eric Evans, 《Domain-Driven Design》 (국내판 《도메인 주도 설계》)
- [ddd-by-examples/library](https://github.com/ddd-by-examples/library): 이벤트 스토밍으로 바운디드 컨텍스트를 나누는 과정이 README에 자세히 있다
- 이 프로젝트의 설계 결정: [ADR-0001 모듈러 모놀리식으로 시작한다](../adr/0001-modular-monolith-first.md), [ADR-0002 바운디드 컨텍스트](../adr/0002-bounded-contexts.md), [ADR-0009 테이블 소유권](../adr/0009-table-ownership.md)
