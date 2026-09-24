# [모놀리식에서 MSA로 #0] 쪼개기 전에, 다른 회사들은 모놀리식을 어떻게 설계했나

> 시리즈 코드는 전부 [monolith-to-msa-lab 저장소](https://github.com/qjatn4793/monolith-to-msa-lab)에 있다.
> 이번 편은 코드 없이 리서치만 다룬다. 레포 스타 수와 링크는 2026년 9월 기준이다.

지난 글([RabbitMQ vs Kafka](https://beomble-bee.tistory.com/51))에서는 메시지 브로커 두 개를 직접 돌려보면서 비교했다. 이번에는 범위를 넓혀서, **모놀리식 서비스를 직접 만들고 그걸 여러 개의 마이크로서비스로 점진적으로 떼어내는 과정**을 시리즈로 기록하려고 한다.

솔직하게 말하면, 나는 지금까지 모놀리식 서비스만 개발하고 운영해왔다. MSA 구조에서 실무를 해본 경험은 없다. 채용 공고와 기술 블로그에는 MSA라는 단어가 당연하다는 듯 등장하는데, 정작 나는 MSA가 정확히 무엇인지, 왜 필요한지, 지금 다루는 모놀리식 서비스와 무엇이 다른지를 설명할 수 없었다.

MSA 전환 글은 많다. 그런데 대부분 "서비스를 도메인별로 나누고, 서비스마다 DB를 따로 두고, 메시지 큐로 통신한다"에서 끝난다. 모놀리식만 다뤄본 입장에서 정작 궁금한 건 그 앞이었다.

- MSA는 무엇이고, **왜** 필요한가?
- 떼어내기 **전의** 모놀리식은 어떻게 생겨야 하는가? 회사들은 실제로 모놀리식 내부를 어떻게 나누는가?
- 떼어낼 때는 무엇부터, 어떤 순서로 하는가? 대기업들은 이 전환을 어떻게 점진적으로 진행해왔는가?

코드를 쓰기 전에 이 질문들을 먼저 정리하기로 했다. 참고할 레포와 사례를 찾아 직접 구조를 뜯어봤고, 이 글은 그 기록이다. 경험이 없는 만큼 남의 결론을 옮겨 적기보다, **직접 만들고 쪼개보면서 확인한 것만 믿기로 했다.** 이 시리즈를 코드와 함께 진행하는 이유다.

---

## 0. 이 시리즈에서 할 것

이커머스 주문 서비스(회원, 상품, 재고, 주문, 결제, 알림)를 **운영 가능한 수준의 모듈러 모놀리식**으로 먼저 만든다. 그다음 성능 기준선을 측정하고, 모듈을 하나씩 떼어내면서 무엇이 깨지는지 기록한다.

| 편 | 내용 |
|---|---|
| **#0** | **리서치: 회사들은 모놀리식을 어떻게 설계하고 떼어내나 (이 글)** |
| #1 | 도메인 분석과 바운디드 컨텍스트 설계 |
| #2 | DDD 애그리거트와 헥사고날 구조 구현 |
| #3 | Spring Modulith로 모듈 경계를 테스트로 강제하기 |
| #4 | 모듈 간 협력: 도메인 이벤트, 결제, 트랜잭션 밖 외부 호출 |
| #5 | 운영 가능한 서비스의 조건 (보안, 관측성, CI/CD) |
| #6~7 | 성능 기준선 측정과 튜닝 |
| #8 | 무엇부터 떼어낼까: 추출 순서 결정 |
| #9 | 첫 서비스 추출 (Gateway, Strangler Fig) |
| #10 | DB 분리와 조회 모델 |
| #11 | Saga와 이벤트 외부화 (Kafka) |
| #12 | 장애 격리 |
| #13 | 전후 비교 회고 |

기술 스택은 Kotlin, Spring Boot 4, Spring Modulith, MySQL이다.

---

## 1. MSA는 무엇이고, 왜 필요한가

### 정의

MSA라는 말을 널리 알린 글은 James Lewis와 Martin Fowler의 [Microservices](https://martinfowler.com/articles/microservices.html)(2014)다. 이 글은 마이크로서비스를 대략 이렇게 설명한다.

> 하나의 애플리케이션을 **작은 서비스들의 모음**으로 만드는 방식. 각 서비스는 **자기 프로세스**에서 돌고, HTTP API나 메시징 같은 **가벼운 방식으로 통신**한다. 서비스는 **비즈니스 기능 단위**로 만들어지고, 각각 **독립적으로 배포**할 수 있다.

모놀리식과 비교하면 차이가 분명해진다.

| | 모놀리식 | MSA |
|---|---|---|
| 배포 단위 | 애플리케이션 전체 하나 | 서비스별 |
| 모듈 간 호출 | 같은 프로세스 안의 메서드 호출 | 네트워크 호출 (HTTP, 메시지) |
| 데이터 | 보통 DB 하나를 공유 | 서비스마다 자기 데이터를 소유 |
| 트랜잭션 | DB 트랜잭션 하나로 묶을 수 있음 | 서비스를 넘으면 묶을 수 없음 |
| 확장 | 전체를 통째로 늘림 | 필요한 서비스만 늘림 |
| 장애 | 한 곳의 문제가 전체 프로세스에 영향 | 격리할 수 있음 (설계를 잘했다면) |

### 왜 필요한가

정의만 보면 "잘게 나누는 것"이 목적처럼 보이지만, 사례를 읽어보면 회사들이 MSA로 가는 이유는 대부분 **기술보다 조직 문제**였다.

1. **독립 배포**: 개발자가 수백 명이 되면 모놀리식 하나를 모두가 함께 배포해야 한다. 한 팀의 변경이 다른 팀의 배포를 막고, 배포 주기가 느려진다.
2. **팀 자율성**: 팀이 자기 서비스의 코드, 데이터, 배포를 소유하면 다른 팀과 조율하지 않고 움직일 수 있다. 시스템 구조는 조직의 커뮤니케이션 구조를 닮는다는 [콘웨이의 법칙](https://martinfowler.com/bliki/ConwaysLaw.html)이 자주 인용되는 이유다.
3. **독립 확장**: 트래픽이 몰리는 기능(예: 주문 조회)만 따로 늘릴 수 있다.
4. **장애 격리**: 알림 서비스가 죽어도 주문은 받을 수 있다.

### 대신 치르는 비용

공짜가 아니다. 모놀리식에서는 당연했던 것들을 잃는다.

- 메서드 호출이 **네트워크 호출**이 되면서 지연, 타임아웃, 부분 실패가 생긴다.
- `@Transactional` 하나로 묶던 작업을 **여러 서비스에 걸친 정합성** 문제로 다시 풀어야 한다.
- 모듈 간 JOIN이 사라지면서 **조회 방식**을 다시 설계해야 한다.
- 배포, 모니터링, 분산 추적, 장애 대응까지 **운영 복잡도**가 서비스 수만큼 늘어난다.

Fowler는 이 비용을 [Microservice Premium](https://martinfowler.com/bliki/MicroservicePremium.html)이라고 부른다. 시스템이 충분히 복잡해서 이 비용을 치를 가치가 있을 때만 MSA가 이득이라는 뜻이다.

> MSA는 목표가 아니라 **조직과 시스템이 커졌을 때 치르는 비용 대비 효과의 선택**이다.
> 이 시리즈에서는 위에 적은 "대신 치르는 비용"을 하나씩 직접 겪어볼 생각이다.

---

## 2. 먼저 짚고 갈 것: MSA 전환은 "쪼개기"부터 시작하지 않는다

리서치를 하면서 가장 먼저 바뀐 생각이 이거다. MSA 전환이라고 하면 서비스를 나누고 배포를 분리하는 장면부터 떠올리게 된다. 그런데 사례를 보면 순서가 반대다. **코드 안에서 경계를 먼저 만들고, 그 경계가 충분히 단단해진 다음에야 프로세스를 나눈다.**

Martin Fowler는 [MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html)에서 처음부터 마이크로서비스로 시작한 프로젝트는 대부분 문제를 겪었다고 말한다. 도메인 경계를 잘 모르는 상태에서 서비스를 나누면, 경계가 틀렸을 때 고치는 비용이 코드 안에서 고칠 때보다 훨씬 크다.

가장 유명한 사례는 Shopify다. Shopify는 1,000명이 넘는 개발자가 10년 넘게 작업해온, 가장 큰 규모의 Rails 코드베이스 중 하나를 운영한다. 2017년에 이걸 쪼개는 프로젝트를 시작했는데, 처음 이름은 "Break-Core-Up-Into-Multiple-Pieces"였다. 결국 선택한 방향은 마이크로서비스가 아니라 **모듈러 모놀리식**이었다. 코드베이스는 하나로 유지하되 컴포넌트 사이의 경계를 정의하고, 그 경계를 도구로 강제한다. ([Deconstructing the Monolith](https://shopify.engineering/deconstructing-monolith-designing-software-maximizes-developer-productivity), [Under Deconstruction](https://shopify.engineering/shopify-monolith))

> 모놀리식과 MSA 사이에는 **모듈러 모놀리식**이라는 단계가 있다.
> 이 시리즈에서 만들 첫 번째 결과물이 바로 그것이다.

---

## 3. 회사들이 공통으로 가져가는 다섯 가지 원칙

회사마다 언어도 프레임워크도 다르지만, 모놀리식을 오래 운영한 곳들의 원칙은 거의 같았다.

### 원칙 1. 최상위는 레이어가 아니라 도메인으로 나눈다

```
# 레이어 기준 (흔하지만 커지면 무너지는 구조)
controller/  OrderController, PaymentController, MemberController ...
service/     OrderService, PaymentService, MemberService ...
repository/  OrderRepository, PaymentRepository ...

# 도메인 기준
order/       controller, service, repository ...
payment/     controller, service, repository ...
member/      ...
```

레이어 기준 구조에서는 `OrderService`가 `PaymentRepository`를 주입받는 걸 막을 방법이 없다. 같은 `service` 패키지, 같은 `repository` 패키지 안에서 모든 게 서로를 볼 수 있기 때문이다. 도메인 기준으로 나눠야 **"주문이 결제의 내부를 건드렸다"는 사실이 구조상 드러난다.**

### 원칙 2. 경계는 사람이 아니라 도구가 지킨다

"다른 모듈의 Repository는 직접 쓰지 말자"는 규칙은 문서에 적어두는 순간부터 깨지기 시작한다. 마감이 급한 날 누군가는 반드시 import 한 줄로 해결한다. 그래서 규모 있는 조직은 경계 위반을 **빌드나 테스트 단계에서 실패**하게 만든다.

| 도구 | 방식 |
|---|---|
| [Shopify/packwerk](https://github.com/Shopify/packwerk) (Ruby) | 패키지 간 상수 참조를 정적 분석해서 허용되지 않은 의존을 잡는다 |
| [Spring Modulith](https://github.com/spring-projects/spring-modulith) | 최상위 패키지를 모듈로 보고, 다른 모듈의 내부 패키지를 참조하면 테스트가 실패한다 |
| ArchUnit | 레이어 간 의존 방향을 테스트 코드로 검증한다 |
| Gradle 멀티모듈 | 의존성을 선언하지 않은 모듈은 컴파일 자체가 안 된다 |

### 원칙 3. 테이블에도 주인이 있다

DB가 하나여도 **모듈마다 자기 테이블만 읽고 쓴다.** 모듈을 넘나드는 JOIN이나 FK를 쓰지 않는다. 나중에 서비스를 떼어낼 때 가장 오래 걸리는 작업이 코드가 아니라 데이터 분리이기 때문이다. 뒤에서 볼 kgrzybek 레포는 아예 모듈마다 DB 스키마를 분리해둔다.

### 원칙 4. 모듈 간 통신은 공개된 계약으로만 한다

다른 모듈과 대화하는 방법은 두 가지뿐이다.

- **동기**: 상대 모듈이 공개한 API(파사드)를 호출한다
- **비동기**: 상대 모듈이 발행한 이벤트를 구독한다

상대 모듈의 엔티티, Repository, 내부 서비스를 직접 참조하지 않는다. 이 원칙을 지켜두면 나중에 "같은 프로세스 안의 메서드 호출"을 "HTTP 호출"이나 "Kafka 메시지"로 바꾸는 작업이 호출부 한 곳으로 좁혀진다.

### 원칙 5. 내부 이벤트와 외부 이벤트를 구분한다

- **도메인 이벤트**: 모듈 **내부**에서 쓰는 이벤트. 도메인 모델이 바뀌면 같이 바뀌어도 된다.
- **통합 이벤트**: 다른 모듈에 **공개**하는 이벤트. 외부와 맺은 계약이라 함부로 바꾸면 안 된다.

둘을 같은 클래스로 쓰면, 내부 리팩터링 한 번에 다른 모듈이 전부 깨진다. MSA로 가면 통합 이벤트는 그대로 Kafka 메시지 스키마가 된다.

---

## 4. 모듈화에는 두 가지 축이 있다

리서치하면서 헷갈렸던 부분이다. "멀티모듈"이라는 같은 단어가 서로 다른 두 가지를 가리킨다.

**기술 축**: API, 도메인, 인프라처럼 **기술 역할**로 Gradle 모듈을 나눈다. 국내에서 가장 널리 읽힌 글은 우아한형제들의 [멀티모듈 설계 이야기 with Spring, Gradle](https://techblog.woowahan.com/2637/)이다. 의존 방향(API → 도메인, 도메인은 인프라를 모름)을 빌드 수준에서 강제하는 게 목적이다.

**도메인 축**: 주문, 결제, 재고처럼 **비즈니스 역할**로 모듈을 나눈다. Shopify의 컴포넌트, Spring Modulith의 모듈이 여기에 해당한다.

둘은 경쟁 관계가 아니라 직교하는 축이다. 그리고 **MSA로 떼어내는 단위는 도메인 축이다.** 기술 축만 나눠둔 코드베이스에서는 `domain` 모듈 안에 주문, 결제, 재고가 여전히 한 덩어리로 섞여 있다.

국내 코드베이스를 보면 엄격한 헥사고날보다 "레이어드 + 기술 축 멀티모듈" 구조가 더 흔하다. 헥사고날은 도메인이 복잡한 곳에 선택적으로 적용하는 경우가 많다. 이 시리즈에서는 **도메인 축을 Spring Modulith로 나누고, 각 모듈 내부는 헥사고날로 구성한다.**

---

## 5. 모듈 내부 구조: 레포 네 개를 뜯어봤다

도메인 축으로 나눈 다음, 모듈 **안쪽**은 어떻게 생겨야 할까. 많이 참고되는 레포 네 개의 패키지 구조를 직접 열어봤다.

### A. [thombergs/buckpal](https://github.com/thombergs/buckpal): 교과서적인 헥사고날 (★2.5k, Java)

《Get Your Hands Dirty on Clean Architecture》(국내판 《만들면서 배우는 클린 아키텍처》)의 예제 코드다.

```
buckpal/
├── adapter/
│   ├── in/web/                  컨트롤러
│   └── out/persistence/         JPA 엔티티, 매퍼, 영속성 어댑터
├── application/
│   ├── domain/model/            도메인 모델 (순수 Java)
│   ├── domain/service/          유스케이스 구현
│   ├── port/in/                 유스케이스 인터페이스
│   └── port/out/                저장소, 외부 호출 인터페이스
└── common/
```

- 포트와 어댑터가 무엇인지 가장 명확하게 보여준다. 도메인 모델과 JPA 엔티티를 분리하고 매퍼로 변환하는 방식도 여기서 볼 수 있다.
- 다만 애플리케이션 전체가 **하나의 헥사곤**이다. 모듈이 여러 개일 때 모듈 사이를 어떻게 연결하는지는 다루지 않는다.

### B. [ddd-by-examples/library](https://github.com/ddd-by-examples/library): DDD 전술 패턴의 정석 (★5.9k, Java)

```
lending/                         바운디드 컨텍스트
├── patron/                      애그리거트 단위
│   ├── model/                   도메인 (Patron, 대출 정책, 도메인 이벤트)
│   ├── application/             유스케이스 (checkout/, hold/)
│   └── infrastructure/          영속성
├── book/
│   ├── model/ · application/ · infrastructure/
├── patronprofile/               조회 전용 모델
│   ├── model/ · infrastructure/ · web/
catalogue/                       다른 바운디드 컨텍스트
commons/                         이벤트 발행 등 공통 기반
```

- `model / application / infrastructure` 3분할이다. 이 시리즈에서 쓰려는 `domain / application / infrastructure`와 가장 가깝다.
- README에 이벤트 스토밍으로 도메인을 분석하는 과정부터 담겨 있다. **코드보다 README가 더 가치 있는 레포**다.
- 쓰기 모델(`patron`)과 조회 모델(`patronprofile`)을 나눈 CQRS 구조도 볼 수 있다.

### C. [kgrzybek/modular-monolith-with-ddd](https://github.com/kgrzybek/modular-monolith-with-ddd): 운영에 가장 가까운 모듈러 모놀리식 (★14k, C#)

.NET 레포지만 모듈러 모놀리식을 공부한다면 반드시 거쳐가게 되는 레포다.

```
Modules/Payments/
├── Domain/                      애그리거트, 도메인 이벤트, SeedWork(공통 기반 클래스)
├── Application/
│   ├── Contracts/               ← 이 모듈의 공개 인터페이스 (Command / Query)
│   └── Subscriptions/, Payers/  기능별 유스케이스
├── Infrastructure/
│   ├── Configuration/
│   ├── Outbox/                  ← 모듈마다 자기 아웃박스를 가진다
│   └── InternalCommands/
├── IntegrationEvents/           ← 다른 모듈에 공개하는 이벤트 (별도 프로젝트)
└── Tests/
    ├── ArchTests/               아키텍처 규칙 테스트
    ├── IntegrationTests/
    └── UnitTests/
```

이 레포에서 가장 중요한 건 **공개 계약(`Contracts`, `IntegrationEvents`)이 내부 구현과 물리적으로 분리돼 있다는 점**이다. 다른 모듈은 이 두 가지만 참조할 수 있다. 모듈별 DB 스키마, 모듈별 아웃박스, 아키텍처 테스트까지 갖추고 있어서 "운영 가능한 모듈러 모놀리식"의 기준점으로 삼기 좋다.

### D. [odrotbohm/spring-restbucks](https://github.com/odrotbohm/spring-restbucks): Spring Modulith 제작자의 스타일 (★1.3k, Java)

```
restbucks/
├── order/      (+ web/, config/)
├── payment/    (+ web/)
├── drinks/
└── core/
```

Spring Modulith를 만든 Oliver Drotbohm의 레포다. 모듈 내부를 레이어로 거의 나누지 않는다. **모듈 경계가 레이어 경계보다 중요하다**는 입장이고, DDD나 레이어 개념은 [jMolecules](https://github.com/xmolecules/jmolecules) 어노테이션으로 코드에 표시한다.

헥사고날을 엄격하게 적용하는 것만이 정답은 아니라는 **반대편 관점**으로 읽으면 좋다.

### 네 레포를 나란히 놓고 보면

| | buckpal | library | kgrzybek | restbucks |
|---|---|---|---|---|
| 모듈(바운디드 컨텍스트) 여러 개 | ✗ | ○ | ○ | ○ |
| 모듈 내부 레이어 분리 | 엄격 | ○ | ○ | 느슨함 |
| 포트/어댑터 명시 | ○ | 부분적 | 부분적 | ✗ |
| 공개 계약 분리 | - | 약함 | **강함** | Modulith 규칙으로 |
| 아웃박스 | ✗ | ✗ | ○ | Modulith 기능으로 |
| 아키텍처 테스트 | ○ | ○ | ○ | ○ (Modulith) |

어느 하나가 정답이라기보다 **보여주려는 게 다르다.** buckpal은 헥사고날 자체, library는 DDD 모델링, kgrzybek은 모듈 간 통합, restbucks는 실용성에 초점이 있다.

---

## 6. 그래서 이 시리즈는 이렇게 간다

세 레포의 장점을 조합했다. 모듈 간 관계는 kgrzybek, 모듈 내부 레이어는 library, 포트와 어댑터는 buckpal에서 가져온다.

아래는 order 모듈의 실제 구조다. 처음 그렸던 설계안에서 구현하며 바뀐 부분이 있는데, 그 과정은 #2에서 다룬다.

```
order/                              ← Spring Modulith 모듈
├── api/                            ← [공개] @NamedInterface("api")
│                                     (#4에서 추가) 다른 모듈용 파사드, 통합 이벤트
├── domain/                         [내부] 순수 Kotlin, Spring/JPA 의존 없음
│   ├── Order.kt, OrderLine.kt      애그리거트
│   └── OrderRepository.kt          저장소 인터페이스
├── application/                    [내부]
│   ├── port/in/                    PlaceOrderUseCase, CancelOrderUseCase, GetOrderQuery
│   ├── port/out/                   LoadOrdererPort, LoadProductPort, StockPort, OrderListPort
│   └── service/                    유스케이스 구현, 트랜잭션 경계
└── infrastructure/                 [내부]
    ├── web/                        인바운드 어댑터 (컨트롤러)
    ├── persistence/                JPA 엔티티와 매퍼 (order 테이블만 접근)
    └── adapter/                    아웃바운드 어댑터: 다른 모듈의 api 호출
        ├── MemberAdapter.kt        LoadOrdererPort 구현 → member.api
        ├── CatalogAdapter.kt       LoadProductPort 구현 → catalog.api
        └── InventoryAdapter.kt     StockPort 구현 → inventory.api
```

다른 모듈이 부르는 쪽(member, catalog, inventory)에는 `api/`에 파사드 인터페이스가 있고, 그 구현은 `infrastructure/facade/`에 있다. 다른 모듈도 웹 클라이언트처럼 이 모듈을 사용하는 쪽이라서, 파사드 구현은 컨트롤러와 같은 인바운드 어댑터로 본다.

규칙은 네 가지다. 전부 테스트로 강제한다.

| 규칙 | 검사하는 테스트 |
|---|---|
| 1. `domain`은 `application`과 `infrastructure`를 모르고, Spring과 JPA에 의존하지 않는다 | `LayerDependencyTests` (ArchUnit) |
| 2. `application`은 `infrastructure`를 모른다 | `LayerDependencyTests` |
| 3. 다른 모듈은 `api` 패키지만 참조할 수 있고, 선언한 모듈에만 의존할 수 있다 | `ModularityTests` (Spring Modulith) |
| 4. `api`는 `infrastructure/adapter`(다른 모듈 호출)와 `infrastructure/facade`(자기 api 구현)에서만 참조한다 | `LayerDependencyTests` |

`domain`과 `application`이 전혀 의존하지 않는 건 아니다. 모든 모듈이 함께 쓰는 공유 커널(`shared`)의 값 객체(`Money` 등)에는 의존한다. `application`은 트랜잭션 때문에 Spring에도 의존한다.

마지막 규칙이 이 시리즈의 핵심이다. `order`가 `inventory`를 아는 곳은 `InventoryAdapter` **한 곳뿐**이고, 그마저도 `inventory.api`만 안다. MSA로 전환하는 날에는 이 어댑터 하나만 HTTP 클라이언트로 갈아끼우면 된다. `PlaceOrderUseCase`와 `Order` 애그리거트는 한 줄도 바뀌지 않아야 한다.

**정말 그렇게 되는지는 #9 이후에 직접 확인할 예정이다.** 어댑터만 바꾸면 끝난다는 건 설계상의 희망일 뿐이고, 실제로는 트랜잭션 경계와 네트워크 지연이라는 문제가 남을 거라고 예상한다.

---

## 7. 떼어낼 때는 어떤 순서로 하나

### 가장 도움이 된 레포: [odrotbohm/sos](https://github.com/odrotbohm/sos)

Oliver Drotbohm의 발표 "Refactoring to a System of Systems"의 예제다. **같은 도메인(catalog, inventory, orders)을 단계별 폴더로 나눠서** 보여준다.

```
00-monolith        뭉쳐 있는 모놀리식
10-microlith       그대로 쪼갠 결과 → 분산 모놀리스 (안티패턴 시연)
20-modulith        먼저 모듈 경계를 정리한 모놀리식
30-messaging-sos   메시징으로 분리한 시스템
40-restful-sos     REST와 이벤트 피드로 분리한 시스템
```

특히 `10-microlith`가 인상적이다. 경계를 정리하지 않고 모놀리식을 그대로 쪼개면, 서비스는 여러 개인데 여전히 서로를 동기로 호출하고 함께 배포해야 하는 **분산 모놀리스**가 된다. 그리고 `20-modulith`에서 한 발 물러나 경계부터 다시 정리한다. 2021년 코드라 Spring 버전은 오래됐지만, 흐름은 이 시리즈 로드맵과 거의 같다.

### 공통 절차

여러 자료를 종합하면 순서는 대체로 이렇다.

**1. 코드 안에서 모듈 경계를 먼저 만든다.** 앞에서 본 모듈러 모놀리식 단계다.

**2. 추출할 모듈을 고른다.** Zhamak Dehghani의 [How to break a Monolith into Microservices](https://martinfowler.com/articles/break-monolith-into-microservices.html)(2018)가 기준을 가장 잘 정리했다.
- 첫 번째는 모놀리식에 의존하지 않는 **가장자리의 단순한 기능**으로 연습한다. 배포 파이프라인과 운영 체계를 먼저 검증하는 게 목적이다.
- 그다음은 **변경이 잦고 비즈니스 가치가 큰 기능**을 떼어낸다.
- 처음에는 서비스를 **크게** 떼어낸다. 필요하면 나중에 더 쪼갠다.
- 각 단계는 그 자체로 완결된 개선이어야 한다. 중간에 멈춰도 이전보다 나은 상태여야 한다.

**3. 데이터 소유권을 분리한다.** 코드보다 데이터가 어렵다. 모듈 간 JOIN과 FK를 제거하고, 스키마를 분리하고, 그다음에 DB를 분리한다. ([How to extract a data-rich service from a monolith](https://martinfowler.com/articles/extract-data-rich-service.html))

**4. 동기 호출 중 이벤트로 바꿀 수 있는 것은 바꾼다.** 이때 "DB에 저장하고 메시지를 발행하는" 이중 쓰기 문제가 생긴다. 이걸 푸는 게 트랜잭셔널 아웃박스다. Spring Modulith는 [아웃박스 예제](https://github.com/spring-projects/spring-modulith/tree/main/spring-modulith-examples/spring-modulith-example-outbox)와 [Kafka로 이벤트를 외부화하는 예제](https://github.com/spring-projects/spring-modulith/tree/main/spring-modulith-examples/spring-modulith-example-kafka)를 제공한다.

**5. 앞단에 게이트웨이를 두고 트래픽을 옮긴다(Strangler Fig).** 경로 단위로 새 서비스에 라우팅하고, 병행 운영으로 결과를 비교한 뒤, 기존 코드를 지운다.

**6. 여러 서비스에 걸친 흐름은 Saga로 다시 설계한다.** `@Transactional` 하나로 묶여 있던 주문, 재고, 결제는 더 이상 하나의 트랜잭션이 아니다. Chris Richardson의 [ftgo-application](https://github.com/microservices-patterns/ftgo-application)(《Microservices Patterns》 예제)에서 구현을 볼 수 있다.

국내 사례로는 [쿠팡의 마이크로서비스 전환기](https://medium.com/coupang-engineering/how-coupang-built-a-microservice-architecture-fd584fff7f2b)와, MSA 환경에서 회원 도메인이 외부 시스템에 영향을 주지도 받지도 않도록 이벤트 계층을 설계한 [우아한형제들 회원시스템 이벤트기반 아키텍처](https://techblog.woowahan.com/7835/)가 참고할 만하다.

---

## 8. 반대로 가는 사례도 있다

MSA가 항상 도착점은 아니다.

**Uber**는 2018년 무렵 마이크로서비스가 2,000개를 넘어섰다. 기능 하나를 바꾸려면 수십 개 팀이 조율해야 했고, 새로 온 엔지니어는 시스템 전체 구조를 파악하기 어려웠다. 그래서 관련 서비스를 도메인 단위로 다시 묶고, 도메인 사이에는 게이트웨이를 두는 [DOMA(Domain-Oriented Microservice Architecture)](https://www.uber.com/us/en/blog/microservice-architecture/)를 도입했다. 너무 잘게 쪼갠 다음 다시 묶은 셈이다.

**Amazon Prime Video**는 2023년에 영상 품질 모니터링 서비스를 서버리스 마이크로서비스(Lambda와 Step Functions)에서 모놀리식으로 바꿔 인프라 비용을 90% 넘게 줄였다고 밝혔다. ([The New Stack 정리](https://thenewstack.io/return-of-the-monolith-amazon-dumps-microservices-for-video-monitoring/)) 오케스트레이션의 상태 전이와 S3를 거치는 중간 데이터 전달 비용이 컸는데, 한 프로세스 안에서 메모리로 주고받게 바꾸면서 그 비용이 사라졌다.

한 가지는 오해하지 않아야 한다. 이건 **Prime Video 전체가 아니라 모니터링 도구 하나**의 이야기다. "MSA는 틀렸다"가 아니라 **"경계를 잘못 그으면 네트워크 비용이 그 경계를 따라 청구된다"**로 읽는 게 맞다고 생각한다.

---

## 9. 정리

리서치를 마치고 이 시리즈의 방향을 세 줄로 정리했다.

1. **모놀리식이 먼저다.** 정확히는 경계가 단단한 **모듈러 모놀리식**이 먼저다. 경계가 틀렸을 때 코드 안에서 고치는 비용이 서비스 사이에서 고치는 비용보다 훨씬 싸다.
2. **경계는 도구로 지킨다.** 모듈 간 통신은 공개 API와 이벤트로만 하고, 테이블에도 주인이 있다. 이걸 테스트로 강제한다.
3. **떼어내는 건 코드가 아니라 데이터와 트랜잭션이다.** 코드는 어댑터 하나를 바꾸면 되도록 설계할 수 있지만, 데이터 소유권과 트랜잭션 경계는 설계만으로 사라지지 않는다.

<!-- TODO(작성자): "틀렸던 것"은 시리즈를 직접 진행하면서 정리해 추가 (마지막 회고 편으로 모아도 됨) -->

다음 편에서는 이커머스 도메인을 분석하고 바운디드 컨텍스트를 나눈다. 특히 **"상품"을 catalog와 inventory 두 개로 나눈 이유**를 다룰 예정이다. 재고는 쓰기가 몰리는 핫 로우라서, 상품 정보와 같은 애그리거트에 두면 성능 편에서 문제가 된다.

---

## 참고 자료

**레포지토리**
- [thombergs/buckpal](https://github.com/thombergs/buckpal): 헥사고날 아키텍처 예제
- [ddd-by-examples/library](https://github.com/ddd-by-examples/library): DDD 전략/전술 설계 예제
- [kgrzybek/modular-monolith-with-ddd](https://github.com/kgrzybek/modular-monolith-with-ddd): 모듈러 모놀리식 레퍼런스
- [odrotbohm/spring-restbucks](https://github.com/odrotbohm/spring-restbucks): Spring Modulith 스타일
- [odrotbohm/sos](https://github.com/odrotbohm/sos): 모놀리식에서 시스템 오브 시스템즈로 단계별 전환
- [spring-projects/spring-modulith](https://github.com/spring-projects/spring-modulith): 아웃박스, Kafka 예제 포함
- [xmolecules/jmolecules](https://github.com/xmolecules/jmolecules): 아키텍처 개념을 코드로 표현하는 라이브러리
- [Shopify/packwerk](https://github.com/Shopify/packwerk): Rails 모놀리식의 경계 강제 도구
- [microservices-patterns/ftgo-application](https://github.com/microservices-patterns/ftgo-application): 《Microservices Patterns》 예제

**글**
- James Lewis, Martin Fowler, [Microservices](https://martinfowler.com/articles/microservices.html)
- Martin Fowler, [MicroservicePremium](https://martinfowler.com/bliki/MicroservicePremium.html) / [ConwaysLaw](https://martinfowler.com/bliki/ConwaysLaw.html)
- Martin Fowler, [MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html)
- Zhamak Dehghani, [How to break a Monolith into Microservices](https://martinfowler.com/articles/break-monolith-into-microservices.html)
- [How to extract a data-rich service from a monolith](https://martinfowler.com/articles/extract-data-rich-service.html)
- Shopify, [Deconstructing the Monolith](https://shopify.engineering/deconstructing-monolith-designing-software-maximizes-developer-productivity) / [Under Deconstruction](https://shopify.engineering/shopify-monolith)
- Uber, [Introducing Domain-Oriented Microservice Architecture](https://www.uber.com/us/en/blog/microservice-architecture/)
- The New Stack, [Return of the Monolith: Amazon Dumps Microservices for Video Monitoring](https://thenewstack.io/return-of-the-monolith-amazon-dumps-microservices-for-video-monitoring/)
- 우아한형제들, [멀티모듈 설계 이야기 with Spring, Gradle](https://techblog.woowahan.com/2637/)
- 우아한형제들, [회원시스템 이벤트기반 아키텍처 구축하기](https://techblog.woowahan.com/7835/)
- 쿠팡, [마이크로서비스 아키텍처로의 전환](https://medium.com/coupang-engineering/how-coupang-built-a-microservice-architecture-fd584fff7f2b)

**책**
- Tom Hombergs, 《Get Your Hands Dirty on Clean Architecture》 (국내판 《만들면서 배우는 클린 아키텍처》)
- Sam Newman, 《Monolith to Microservices》
- Chris Richardson, 《Microservices Patterns》
