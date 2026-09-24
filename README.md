# monolith-to-msa-lab

모놀리식 서비스를 MSA로 **점진적으로** 전환하면서 주의할 점을 직접 확인하는 실험 코드다.

이커머스 주문 서비스를 **운영 가능한 수준의 모듈러 모놀리식**으로 먼저 만들고, 성능 기준선을 측정한 다음, 모듈을 하나씩 떼어내면서 무엇이 깨지는지 기록한다.
단계마다 git 태그와 블로그 글을 남긴다.

## 진행 상황

글 번호(#)는 블로그 연재 순서, 구현 단계(P)는 코드 작업 단위다. 글 하나가 여러 단계를 다루기도 한다.

| 글 | 주제 | 구현 단계 | 구현 | 글 |
|---|---|---|---|---|
| #0 | 리서치: 회사들은 모놀리식을 어떻게 설계하고 떼어내나 | P0 | ✅ | [초안](docs/posts/00-how-companies-design-monoliths.md) |
| #1 | 도메인 분석과 바운디드 컨텍스트 | P0 | ✅ | [초안](docs/posts/01-domain-and-bounded-contexts.md) |
| #2 | DDD 애그리거트와 헥사고날 구조 | P1 | ✅ | [초안](docs/posts/02-aggregates-and-hexagonal.md) |
| #3 | Spring Modulith로 모듈 경계를 테스트로 강제하기 | P0, P1 | ✅ | |
| #4 | 모듈 간 협력: 도메인 이벤트, 결제, 트랜잭션 밖 외부 호출 | P2 | | |
| #5 | 운영 가능한 서비스의 조건 (보안, 관측성, CI/CD) | P3 | | |
| #6~7 | 성능 기준선과 튜닝 | P4 | | |
| #8 | 무엇부터 떼어낼까: 추출 순서 결정 | P5 | | |
| #9 | 첫 서비스 추출 (Gateway, Strangler Fig) | P6 | | |
| #10 | DB 분리와 조회 모델 | P7 | | |
| #11 | Saga와 이벤트 외부화 (Kafka) | P8 | | |
| #12 | 장애 격리 | P9 | | |
| #13 | 전후 비교 회고 | P10 | | |

참고 자료: [설계 결정(ADR)](docs/adr/README.md) · [P1 코드 따라가기](docs/guides/p1-code-walkthrough.md)

## 기술 스택

| 영역 | 선택 |
|---|---|
| 언어 | Kotlin 2.3, Java 21 |
| 프레임워크 | Spring Boot 4.1 |
| 모듈 경계 | Spring Modulith 2.1, ArchUnit |
| DB | MySQL 8.4, Flyway, Spring Data JPA |
| 테스트 | JUnit 5, Testcontainers |
| 로컬 인프라 | docker compose |

이후 단계에서 필요해지는 시점에 추가한다: Spring Cloud Gateway, Kafka, Resilience4j, OpenTelemetry, Prometheus/Grafana, k6, Toxiproxy.

## 구조

```
monolith-to-msa-lab/
├── monolith/                          모듈러 모놀리식
│   └── src/main/
│       ├── java/com/beomsoo/shop/     모듈 선언 (package-info.java)
│       └── kotlin/com/beomsoo/shop/
│           ├── shared/                공유 커널 (Money, 에러 응답, ID 생성)
│           ├── member/                회원
│           ├── catalog/               상품 정보
│           ├── inventory/             재고
│           ├── order/                 주문
│           ├── payment/               결제
│           └── notification/          알림
├── docs/
│   ├── adr/                           설계 결정 기록
│   ├── guides/                        코드 따라가기 가이드
│   └── posts/                         블로그 초안
└── docker-compose.yml
```

각 모듈의 내부 구조는 다음과 같다 ([ADR-0003](docs/adr/0003-module-internal-structure.md)).

```
<module>/
├── api/             [공개] 다른 모듈이 참조할 수 있는 유일한 패키지
├── domain/          [내부] 순수 Kotlin 도메인 모델
├── application/     [내부] 유스케이스, 포트
└── infrastructure/  [내부] 어댑터 (web, facade, persistence, adapter)
```

모듈 경계와 레이어 규칙은 테스트로 강제한다.

- `ModularityTests`: 모듈 간 의존 방향, 공개 계약(`api`) 외 참조 금지
- `LayerDependencyTests`: 모듈 안쪽 레이어의 의존 방향, 도메인의 순수성, 모듈 간 호출은 `infrastructure/adapter`와 `facade`에서만

## API

| 모듈 | 메서드 | 경로 | 설명 |
|---|---|---|---|
| member | POST | `/members` | 가입 |
| | GET | `/members/{id}` | 조회 |
| | PATCH | `/members/{id}` | 이름 변경 |
| | POST | `/members/{id}/withdraw` | 탈퇴 |
| catalog | POST | `/products` | 상품 등록 |
| | GET | `/products/{id}`, `/products?page=&size=` | 조회, 목록 |
| | PATCH | `/products/{id}/price` | 가격 변경 |
| | POST | `/products/{id}/stop-selling`, `/resume-selling` | 판매 중지, 재개 |
| inventory | POST | `/stocks/{productId}/receive` | 입고 |
| | GET | `/stocks/{productId}` | 재고 조회 |
| order | POST | `/orders` | 주문 (주문자 확인 → 상품 스냅샷 → 재고 예약) |
| | GET | `/orders/{id}`, `/orders?memberId=&page=&size=` | 조회, 회원별 목록 |
| | POST | `/orders/{id}/cancel` | 취소 (재고 예약 해제) |

에러는 모두 [RFC 9457 ProblemDetail](https://datatracker.ietf.org/doc/html/rfc9457) 형식이고, `code` 필드로 원인을 구분한다.

```json
{ "status": 409, "code": "OUT_OF_STOCK", "detail": "재고가 부족한 상품이 있습니다: [...]", ... }
```

## 실행 방법

```bash
docker compose up -d                 # MySQL
./gradlew :monolith:bootRun          # 애플리케이션

curl localhost:8080/actuator/health
curl localhost:8080/actuator/modulith   # 모듈 구조와 허용 의존
```

테스트는 Testcontainers로 MySQL을 직접 띄우므로 Docker만 실행 중이면 된다.

```bash
./gradlew :monolith:test
```

모듈 다이어그램은 테스트 실행 후 `monolith/build/spring-modulith-docs/`에 생성된다.

## 설계 결정

[docs/adr](docs/adr/README.md)에 정리했다.
