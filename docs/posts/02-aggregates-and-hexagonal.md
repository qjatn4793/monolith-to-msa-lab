# [모놀리식에서 MSA로 #2] 모듈 안쪽 설계: DDD 애그리거트와 헥사고날 구조

> 시리즈 코드는 전부 [monolith-to-msa-lab 저장소](https://github.com/qjatn4793/monolith-to-msa-lab)에 있다.
> 이 글에서 다루는 설계 결정은 [ADR-0003](../adr/0003-module-internal-structure.md), [ADR-0004](../adr/0004-separate-domain-and-jpa-model.md), [ADR-0007](../adr/0007-application-generated-uuid-v7.md)에 정리해 두었다.
> 이 글의 테스트 실행 시간은 로컬(JDK 21, Docker 위 MySQL 8.4)에서 한 번 돌린 값이다.

[지난 글(#1)](01-domain-and-bounded-contexts.md)에서는 서비스를 여섯 개 모듈로 나눴다. 이번 글은 그 모듈 **안쪽**이다.

"DDD와 헥사고날 아키텍처로 만들었다"는 말은 흔하다. 그런데 막상 코드를 쓰려고 하면 막히는 질문이 많았다.

- 애그리거트는 그냥 엔티티와 무엇이 다른가?
- 포트와 어댑터는 실제로 어떤 클래스가 되는가?
- 도메인 모델과 JPA 엔티티를 **굳이** 나눠야 하는가? 나누면 무엇을 잃는가?
- 처음 그린 설계대로 구현이 되는가?

이번 글은 이 질문에 대한 답을 실제 코드와 테스트로 정리한다.

<!-- TODO(작성자): 지금까지 모놀리식을 개발하면서 익숙했던 구조(예: Controller-Service-Repository 레이어드)와 비교한 첫인상을 한두 문단 -->

---

## 0. 한 장으로 보는 모듈 구조

가장 단순한 member 모듈을 예로 들면 이렇다.

```
member/
├── api/                          [공개] 다른 모듈이 볼 수 있는 유일한 패키지
│   └── MemberFacade.kt           인터페이스 + 공개 DTO(MemberInfo)
├── domain/                       [내부] 순수 Kotlin
│   ├── Member.kt                 애그리거트 루트
│   ├── Email.kt, MemberId.kt     값 객체
│   ├── MemberRepository.kt       저장소 인터페이스
│   └── MemberExceptions.kt
├── application/                  [내부]
│   ├── port/in/                  JoinMemberUseCase, ChangeMemberUseCase, GetMemberQuery
│   └── service/                  MemberCommandService, MemberQueryService
└── infrastructure/               [내부]
    ├── web/                      MemberController (HTTP → 유스케이스)
    ├── facade/                   MemberFacadeAdapter (다른 모듈 → 유스케이스)
    └── persistence/              MemberJpaEntity, MemberPersistenceAdapter
```

의존은 바깥에서 안쪽으로만 흐른다.

```
infrastructure ──▶ application ──▶ domain
       └───────────────────────────▶
```

`domain`은 `application`도 `infrastructure`도 모르고, Spring과 JPA도 모른다. 이 규칙은 ArchUnit 테스트(`LayerDependencyTests`)가 강제한다. 규칙을 어기면 빌드가 실패한다.

---

## 1. 애그리거트: 규칙을 가진 객체

### 규칙이 서비스에 흩어진 코드

흔히 볼 수 있는 형태는 이런 코드다. 엔티티는 데이터만 들고 있고, 규칙은 서비스가 검사한다.

```kotlin
// 흔한 형태 (이 프로젝트의 코드가 아니다)
@Entity
class Member(var email: String, var name: String, var status: String)

@Service
class MemberService(...) {
    fun withdraw(id: Long) {
        val member = repository.findById(id).get()
        if (member.status == "WITHDRAWN") throw ...   // 규칙이 서비스에 있다
        member.status = "WITHDRAWN"                    // 누구나 상태를 바꿀 수 있다
    }
}
```

이 구조에서는 `member.status = "WITHDRAWN"`을 **어디서든** 쓸 수 있다. 탈퇴한 회원은 이름을 바꿀 수 없다는 규칙이 있다면, 이름을 바꾸는 모든 서비스 메서드가 그 규칙을 기억해야 한다. 하나라도 빠뜨리면 규칙이 깨진다.

### 규칙을 객체 안에 두기

이 프로젝트의 `Member`는 이렇다.

```kotlin
// member/domain/Member.kt
class Member(
    val id: MemberId,
    val email: Email,
    name: String,
    status: MemberStatus,
    val joinedAt: Instant,
) {
    var name: String = validateName(name)
        private set                        // 밖에서 직접 바꿀 수 없다

    var status: MemberStatus = status
        private set

    fun changeName(newName: String) {
        ensureActive()                     // 탈퇴한 회원이면 여기서 막힌다
        name = validateName(newName)
    }

    fun withdraw() {
        ensureActive()
        status = MemberStatus.WITHDRAWN
    }
    ...
}
```

- 상태는 `private set`이라 **메서드를 통해서만** 바뀐다.
- 상태가 바뀔 때마다 규칙을 검사한다. "탈퇴한 회원은 이름을 바꿀 수 없다"는 규칙이 `changeName` 안에 있으니, 누가 호출하든 빠뜨릴 수 없다.
- 생성자에서도 이름을 검증한다. 규칙을 어긴 `Member`는 **만들어질 수 없다.**

이게 애그리거트의 핵심이다. 애그리거트는 **자기 규칙을 스스로 지키는 객체의 묶음**이고, 바깥은 애그리거트 루트의 메서드를 통해서만 안쪽을 바꿀 수 있다.

### 애그리거트의 범위: 주문과 주문 상품

`Order`는 `OrderLine`(주문 상품)을 품는 애그리거트다. `OrderLine`은 주문 없이 존재할 수 없고, 주문 밖에서 따로 만들거나 바꾸지 않는다.

```kotlin
// order/domain/Order.kt
companion object {
    fun place(memberId: UUID, lines: List<OrderLine>, now: Instant): Order =
        Order(OrderId.new(), memberId, lines, OrderStatus.PENDING, now)

    private fun validateLines(lines: List<OrderLine>): List<OrderLine> {
        if (lines.isEmpty()) throw InvalidInputException("주문 상품이 없습니다.")
        if (lines.size > MAX_LINES) throw InvalidInputException("한 번에 주문할 수 있는 상품은 ${MAX_LINES}종류까지입니다.")
        if (lines.distinctBy { it.productId }.size != lines.size) {
            throw InvalidInputException("같은 상품이 중복으로 들어 있습니다. 수량을 합쳐서 주문해 주세요.")
        }
        return lines.toList()
    }
}
```

"주문 상품은 1~50종류, 같은 상품 중복 금지"는 주문 상품 하나가 아니라 **주문 전체**에 걸린 규칙이다. 그래서 `Order`가 검사한다. 애그리거트의 경계는 "이 규칙을 한 번에 지켜야 하는 범위"로 정한다고 보면 된다.

반대로 회원이나 상품은 주문 애그리거트 밖이다. 그래서 객체가 아니라 ID로만 참조한다 (#1의 5절).

### 값 객체: 존재하면 항상 올바른 값

규칙은 애그리거트에만 있는 게 아니다. 이메일 형식, 금액이 음수가 아닐 것 같은 규칙은 **값 자체**의 규칙이다.

```kotlin
// member/domain/Email.kt
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (normalized.length > MAX_LENGTH || !PATTERN.matches(normalized)) {
                throw InvalidInputException("올바른 이메일 형식이 아닙니다: $raw")
            }
            return Email(normalized)
        }
    }
}
```

생성자가 `private`이라 `Email.of()`를 거치지 않고는 만들 수 없다. 그러니 `Email` 타입의 값이 있다면 **항상 올바른 이메일**이다. 이 값을 받는 코드는 형식을 다시 검사할 필요가 없다.

금액도 마찬가지다. `Money`는 음수를 거부하고, 곱셈에서 오버플로우가 나면 조용히 넘어가지 않고 예외를 던진다.

```kotlin
// shared/domain/Money.kt
@JvmInline
value class Money(val amount: Long) : Comparable<Money> {
    init {
        if (amount < 0) throw InvalidInputException("금액은 0 이상이어야 합니다: $amount")
    }
    operator fun times(quantity: Int): Money = Money(Math.multiplyExact(amount, quantity.toLong()))
}
```

Kotlin의 `value class`는 런타임에 대부분 감싼 값(`String`, `Long`)으로 컴파일된다. 그래서 타입 안전성을 얻으면서도 객체를 새로 만드는 비용이 거의 없다.

### 도메인은 현재 시각을 직접 구하지 않는다

`Member.join(email, name, now)`처럼 시각을 인자로 받는다. 서비스가 `Clock`에서 시각을 얻어 넘긴다. 도메인이 `Instant.now()`를 직접 부르지 않으니, 테스트에서 시각을 고정할 수 있다. 작은 규칙이지만 테스트를 써보면 차이가 크다.

---

## 2. 헥사고날: 포트와 어댑터는 실제로 어떤 코드인가

헥사고날 아키텍처(포트와 어댑터)의 설명을 읽으면 육각형 그림이 나온다. 코드로 옮기면 네 종류의 클래스가 된다.

| 종류 | 역할 | 이 프로젝트의 예 |
|---|---|---|
| 인바운드 포트 | 모듈이 바깥에 제공하는 기능 (인터페이스) | `JoinMemberUseCase`, `PlaceOrderUseCase` |
| 인바운드 어댑터 | 바깥의 요청을 포트 호출로 바꾼다 | `MemberController`, `MemberFacadeAdapter` |
| 아웃바운드 포트 | 모듈이 바깥에 요구하는 기능 (인터페이스) | `MemberRepository`, `LoadOrdererPort`, `StockPort` |
| 아웃바운드 어댑터 | 아웃바운드 포트를 실제 기술로 구현한다 | `MemberPersistenceAdapter`, `MemberAdapter`, `InventoryAdapter` |

### 인바운드: 컨트롤러는 서비스가 아니라 유스케이스를 안다

```kotlin
// member/application/port/in/JoinMemberUseCase.kt
interface JoinMemberUseCase {
    fun join(command: JoinMemberCommand): MemberId
}
data class JoinMemberCommand(val email: String, val name: String)
```

컨트롤러는 이 인터페이스에 의존한다. 요청 DTO를 커맨드로 바꿔 넘길 뿐, 비즈니스 판단은 하지 않는다.

서비스는 **흐름만 조율**한다. 불러오고, 도메인에게 시키고, 저장한다.

```kotlin
// member/application/service/MemberCommandService.kt
override fun join(command: JoinMemberCommand): MemberId {
    val email = Email.of(command.email)
    if (memberRepository.existsByEmail(email)) throw DuplicateEmailException(email)

    val member = Member.join(email, command.name, clock.instant())
    memberRepository.save(member)
    return member.id
}
```

"이름은 50자 이하" 같은 규칙은 서비스에 없다. `Member.join`이 알아서 검사한다. 서비스에 남는 건 "이메일 중복 확인"처럼 **여러 회원을 봐야 하는 규칙**뿐이다. 한 애그리거트 안에서는 판단할 수 없기 때문이다.

### 아웃바운드: 포트는 내 모듈의 언어로 정의한다

아웃바운드 포트에서 가장 중요하게 본 점은 **포트를 누구의 언어로 정의하느냐**다. order 모듈이 회원 정보를 가져오는 포트는 member의 언어가 아니라 order의 언어다.

```kotlin
// order/application/port/out/LoadOrdererPort.kt
interface LoadOrdererPort {
    fun loadOrderer(memberId: UUID): Orderer?
}
data class Orderer(val memberId: UUID, val active: Boolean)
```

주문에 필요한 건 "주문할 수 있는 회원인가"뿐이다. 그래서 이메일도 이름도 가져오지 않는다. 이 포트를 구현하는 어댑터가 member의 공개 API를 부른다.

```kotlin
// order/infrastructure/adapter/MemberAdapter.kt
override fun loadOrderer(memberId: UUID): Orderer? =
    memberFacade.findMember(memberId)?.let { Orderer(memberId = it.id, active = it.active) }
```

이렇게 해두면 `OrderCommandService`의 import 목록에 `member`, `catalog`, `inventory` 패키지가 **하나도 없다.** 실제로 order 모듈 전체에서 member를 import 하는 파일은 `MemberAdapter` 하나뿐이다. member를 별도 서비스로 떼어내는 날에는 이 파일만 HTTP 클라이언트 호출로 바꾸면 된다.

### 파사드 구현도 인바운드 어댑터다

다른 모듈이 member를 부를 때 쓰는 `MemberFacade`는 `api` 패키지에 인터페이스만 있다. 구현은 `infrastructure/facade`에 둔다.

```
HTTP 요청   ─▶ web/MemberController        ─┐
                                              ├─▶ port/in (유스케이스) ─▶ service ─▶ domain
order 모듈  ─▶ facade/MemberFacadeAdapter  ─┘
```

처음에는 파사드 구현을 `application`에 둘지 고민했다. 그러다 관점을 바꿨다. member 입장에서는 HTTP 클라이언트든 order 모듈이든 똑같이 **바깥에서 온 사용자**다. 그러니 파사드 구현은 컨트롤러와 같은 인바운드 어댑터다. 실제로 `MemberFacadeAdapter`는 컨트롤러처럼 인바운드 포트(`GetMemberQuery`)를 호출하고, 결과를 공개 DTO로 바꿀 뿐이다.

---

## 3. 도메인 모델과 JPA 엔티티를 분리했다. 굳이?

이 프로젝트에서 가장 비용이 큰 결정이다. 도메인 클래스에 `@Entity`를 붙이면 코드가 절반으로 준다. 그래도 나눈 이유는 셋이다.

1. **JPA가 도메인 설계를 제약한다.** 기본 생성자, `open` 클래스, 가변 필드, 프록시. `private set`과 생성자 검증 같은 1절의 설계가 JPA 요구사항과 자주 부딪힌다.
2. **모듈 경계를 넘는 연관관계가 쉽게 생긴다.** `@ManyToOne val member: Member` 한 줄이면 주문이 회원 엔티티를 직접 참조한다. 그러면 모듈 경계를 넘는 FK와 JOIN이 따라온다.
3. **눈에 보이지 않는 동작이 도메인에 섞인다.** 지연 로딩, 변경 감지(dirty checking)가 도메인 로직의 결과를 바꿀 수 있다.

### 어떻게 나눴나

JPA 엔티티는 `infrastructure/persistence`에 따로 있고, 변환만 한다.

```kotlin
// member/infrastructure/persistence/MemberJpaEntity.kt
@Entity
@Table(name = "member")
class MemberJpaEntity(
    @Id val id: UUID,
    val email: String,
    var name: String,
    @Enumerated(EnumType.STRING) var status: MemberStatus,
    val joinedAt: Instant,
) {
    @Version
    var version: Long? = null

    fun update(member: Member) { name = member.name; status = member.status }
    fun toDomain(): Member = Member(MemberId(id), Email.of(email), name, status, joinedAt)
    companion object { fun from(member: Member) = MemberJpaEntity(...) }
}
```

저장은 영속성 어댑터가 맡는다.

```kotlin
// member/infrastructure/persistence/MemberPersistenceAdapter.kt
override fun save(member: Member) {
    val entity = jpaRepository.findByIdOrNull(member.id.value)
    if (entity == null) {
        jpaRepository.save(MemberJpaEntity.from(member))   // 새 회원 → INSERT
    } else {
        entity.update(member)                              // 기존 회원 → 커밋 시 변경 감지로 UPDATE
    }
}
```

여기서 두 가지를 신경 썼다.

**낙관적 락이 그대로 동작해야 한다.** 도메인 모델은 `version`을 모른다. 만약 저장할 때마다 도메인에서 엔티티를 새로 만들면 `version`이 사라져 낙관적 락이 무력해진다. 그래서 이미 있는 엔티티에 상태를 **옮겨 담는** 방식을 택했다. 같은 트랜잭션에서 서비스가 이미 이 회원을 불러왔기 때문에 `findByIdOrNull`은 1차 캐시에서 꺼내고, 추가 쿼리가 나가지 않는다.

**`@Version var version: Long? = null`은 낙관적 락 말고도 역할이 있다.** 이 프로젝트는 ID를 애플리케이션이 만든다(4절). ID가 이미 채워진 엔티티를 Spring Data의 `save()`에 넘기면, Spring Data는 이게 새 엔티티인지 판단하지 못한다. 그러면 SELECT를 한 번 날려본 뒤 `merge`를 한다. `version`이 `null`이면 새 엔티티로 판단하고 바로 `persist` 한다.

### 치른 비용

| 모듈 | 파일 수 | 줄 수 | 제공하는 API |
|---|---|---|---|
| member | 17 | 473 | 가입, 조회, 이름 변경, 탈퇴 |
| catalog | 17 | 486 | 등록, 조회, 목록, 가격 변경, 판매 중지/재개 |
| inventory | 14 | 390 | 입고, 조회, 예약, 해제 |
| order | 22 | 636 | 주문, 조회, 목록, 취소 |

member는 API 네 개에 파일이 17개다. 레이어드 구조였다면 Controller, Service, Repository, Entity, DTO 정도로 끝났을 분량이다. 포트 인터페이스, 매퍼, 어댑터가 그 차이다.

이 비용이 값어치를 하는지는 이 시리즈 후반부가 판정할 것이다. 모듈을 서비스로 떼어낼 때 `domain`과 `application`을 **그대로 옮길 수 있는지**가 기준이다.

---

## 4. ID는 애플리케이션이 만든다: UUID v7

DB의 auto increment를 쓰면 저장하기 전까지 ID가 없다. 그러면 도메인 모델의 ID가 `id: Long?`처럼 nullable이 되고, 코드 곳곳에 `!!`가 생긴다. MSA를 전제로 하면 문제가 하나 더 있다. 서비스마다 DB가 따로 있으면 번호가 겹친다.

그래서 ID는 애플리케이션이 만든다. 흔히 쓰는 랜덤 UUID(v4)는 인덱스 성능이 나쁘다. B-Tree 인덱스 중간중간에 끼어들기 때문이다. 대신 **UUID v7**(RFC 9562)을 쓴다. 앞 48비트가 밀리초 타임스탬프라 생성 순서대로 정렬되고, auto increment처럼 인덱스 끝에 추가된다.

Kotlin 표준 라이브러리에 `Uuid.generateV7()`이 있어서 별도 라이브러리 없이 한 줄로 끝났다.

```kotlin
// shared/domain/Ids.kt
fun newId(): UUID = Uuid.generateV7().toJavaUuid()
```

실제로 서버를 띄우고 회원, 상품, 주문을 차례로 만들었을 때 발급된 ID다.

```
member  01a0d3d8-96aa-7744-8a17-e8d5e36acd64
product 01a0d3d8-96d3-7246-86ad-a4433784f811
order   01a0d3d8-971d-7063-8987-78d73023566b
```

앞자리(`01a0d3d8-96..`)가 시간순으로 증가하고, 세 번째 묶음의 첫 글자가 버전 `7`이다. 덕분에 목록 조회에서 `ORDER BY id DESC`가 곧 최신순이다.

도메인에서는 ID를 모듈별 값 클래스(`MemberId`, `ProductId`, `OrderId`)로 감싼다. `OrderId`가 들어가야 할 자리에 `MemberId`를 넣으면 컴파일이 안 된다. 다만 **다른 모듈의 ID는 `UUID`로 들고 있는다.** 다른 모듈의 값 클래스는 내부 타입이라 참조할 수 없기 때문이다.

---

## 5. 테스트가 쉬워진다: 수치로 보기

헥사고날 구조의 이득이 가장 분명하게 드러나는 곳은 테스트다.

주문 서비스는 포트에만 의존한다. 그래서 포트를 가짜 구현(fake)으로 바꿔 끼우면 **Spring도 DB도 다른 모듈도 없이** 테스트할 수 있다.

```kotlin
// OrderCommandServiceTest.kt
private val orders = FakeOrderRepository()
private val orderers = FakeOrdererPort(Orderer(memberId, active = true))
private val products = FakeProductPort(keyboard, mouse)
private val stock = FakeStockPort()
private val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)

private val service = OrderCommandService(orders, orderers, products, stock, clock)

@Test
fun `재고가 부족하면 주문을 저장하지 않는다`() {
    stock.outOfStock = listOf(keyboard.productId)

    assertThrows<OutOfStockException> { service.place(command(keyboard.productId to 1)) }
    assertTrue(orders.isEmpty())
}
```

가짜 구현은 몇 줄이면 된다. 포트 인터페이스가 작기 때문이다. Mockito 같은 목 라이브러리도 쓰지 않았다.

실행 시간을 비교하면 차이가 분명하다.

| 테스트 | 개수 | Spring | DB | 실행 시간 |
|---|---|---|---|---|
| 도메인 테스트 (`MemberTest`, `OrderTest` 등 5개 클래스) | 19 | ✗ | ✗ | 모두 합쳐 0.012초 |
| `OrderCommandServiceTest` (가짜 포트) | 5 | ✗ | ✗ | 0.002초 |
| `OrderFlowIntegrationTest` (HTTP → MySQL) | 6 | ○ | ○ | 6.19초 |

통합 테스트의 6.19초는 대부분 Spring 컨텍스트를 띄우는 시간이다. 테스트 케이스 자체는 합쳐서 0.67초였다. MySQL 컨테이너는 앞서 실행된 다른 테스트가 띄워둔 것을 재사용했다(그 테스트는 10.48초). 통합 테스트가 필요 없다는 뜻이 아니다. 모듈끼리 실제로 협력하는지는 통합 테스트로만 확인할 수 있다. 다만 **비즈니스 흐름의 분기(없는 회원, 판매 중지 상품, 재고 부족 등)를 전부 통합 테스트로 확인할 필요는 없다.**

---

## 6. 처음 그린 설계는 구현하며 이렇게 바뀌었다

[#0](00-how-companies-design-monoliths.md)을 쓸 때 order 모듈의 구조를 미리 그려두었다. 구현을 마치고 비교해 보니 꽤 달라졌다.

| 처음 설계 | 구현 결과 | 바뀐 이유 |
|---|---|---|
| `ReserveStockPort` | `StockPort` (예약 + 해제) | 주문 취소를 넣으면서 재고 해제가 필요해졌다. 같은 대상(재고)에 대한 요구라 한 포트로 합쳤다. |
| 도메인 저장소에 목록 조회 | 조회 포트(`OrderListPort`)로 분리 | 아래 설명 |
| 파사드 구현 위치를 정하지 않음 | `infrastructure/facade` | 다른 모듈도 "바깥 사용자"로 보기로 했다 (2절) |
| `RequestPaymentPort`, 도메인 이벤트 | 아직 없음 | 결제와 이벤트는 #4에서 다룬다 |

두 번째 변경이 가장 많은 걸 보여준다. 처음에는 상품 목록 조회를 도메인 저장소에 넣으려고 했다.

```kotlin
interface ProductRepository {
    fun findPage(query: PageQuery): PageResult<Product>   // 처음 생각
}
```

그런데 `PageQuery`는 `shared.application` 패키지에 있다. 도메인이 애플리케이션 계층에 의존하게 되니 "domain은 application을 모른다" 규칙에 어긋난다.

처음에는 `PageQuery`를 `shared.domain`으로 옮기면 되지 않을까 생각했다. 그러다 질문을 바꿨다. **페이지 조회는 도메인 규칙인가?** 아니다. "20개씩 최신순으로 보여줘"는 화면의 요구다. 그래서 도메인 저장소에서 빼서 애플리케이션의 조회 포트로 옮겼다.

```kotlin
// catalog/application/port/out/ProductListPort.kt
interface ProductListPort {
    fun findPage(query: PageQuery): PageResult<Product>
}
```

지금은 조회 포트도 도메인 객체를 돌려준다. 하지만 성능 편(#6~7)에서 목록 조회를 조회 전용 모델로 바꾸더라도 도메인은 영향을 받지 않는다. **의존 규칙이 설계를 바로잡아 준 셈이다.**

---

## 7. 아직 모르는 것

1. **단순한 모듈에도 이 구조가 필요한가.** 알림 모듈(#4)은 이벤트를 받아서 보내기만 한다. 포트, 서비스, 어댑터를 전부 둘지, 필요한 레이어만 둘지는 구현하면서 판단한다.
2. **매핑 비용은 얼마인가.** 도메인 ↔ 엔티티 변환이 성능에 영향을 주는지 #6~7에서 측정한다.
3. **값 컬렉션의 N+1.** 주문 상품(`OrderLine`)을 값 컬렉션(`@ElementCollection`)으로 매핑했다. 그래서 주문 목록을 조회하면 주문 수만큼 추가 쿼리가 나간다. 알고 있지만 성능 편의 재료로 남겨뒀다.

---

## 8. 정리

- **애그리거트**는 자기 규칙을 스스로 지키는 객체다. 상태는 메서드로만 바뀌고, 규칙을 어긴 객체는 만들어질 수 없다. 서비스에는 흐름과 "여러 애그리거트를 봐야 하는 규칙"만 남는다.
- **포트와 어댑터**는 코드로 옮기면 네 종류의 클래스가 된다. 아웃바운드 포트는 **내 모듈의 언어**로 정의한다. 그러면 다른 모듈을 아는 곳이 어댑터 하나로 좁혀진다.
- **도메인과 JPA 엔티티를 분리**하면 도메인이 순수해지고 테스트가 쉬워진다. 대가로 파일이 늘어난다(member: API 4개에 파일 17개).
- **ID는 UUID v7**로 애플리케이션이 만든다. 저장 전에도 ID가 있고, 시간순으로 정렬된다.
- 처음 그린 설계는 구현하며 바뀌었다. 특히 **의존 규칙이 잘못된 설계를 드러내 줬다.**

<!-- TODO(작성자): 직접 구현하면서(또는 가이드의 실습 과제를 해보면서) 느낀 점 한두 줄 -->

다음 편(#3)에서는 이 규칙들을 **어떻게 테스트로 강제하는지** 다룬다. Spring Modulith와 ArchUnit이 각각 무엇을 검사하는지 본다. 그리고 규칙을 만들었는데 위반을 **잡지 못했던** 사례도 다룬다.

---

## 참고 자료

- [thombergs/buckpal](https://github.com/thombergs/buckpal): 포트와 어댑터의 패키지 구성 (《만들면서 배우는 클린 아키텍처》 예제)
- [ddd-by-examples/library](https://github.com/ddd-by-examples/library): 애그리거트 설계와 정책 객체
- [RFC 9562](https://www.rfc-editor.org/rfc/rfc9562): UUID v7 명세
- 이 프로젝트의 코드 따라가기: [P1 코드 가이드](../guides/p1-code-walkthrough.md)
- 설계 결정: [ADR-0003](../adr/0003-module-internal-structure.md), [ADR-0004](../adr/0004-separate-domain-and-jpa-model.md), [ADR-0007](../adr/0007-application-generated-uuid-v7.md)
