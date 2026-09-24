# monolith-to-msa-lab

모놀리식 서비스를 MSA로 **점진적으로** 전환하면서 주의할 점을 직접 확인하는 실험 코드다.

이커머스 주문 서비스를 **운영 가능한 수준의 모듈러 모놀리식**으로 먼저 만들고, 성능 기준선을 측정한 다음, 모듈을 하나씩 떼어내면서 무엇이 깨지는지 기록한다.
단계마다 git 태그와 블로그 글을 남긴다.

## 진행 상황

| 단계 | 내용 | 상태 | 글 |
|---|---|---|---|
| P0 | 리서치, 설계 결정(ADR), 프로젝트 뼈대, 모듈 경계 검증 | ✅ | [#0 초안](docs/posts/00-how-companies-design-monoliths.md) |
| P1 | 도메인 모델과 헥사고날 구현 (member, catalog, inventory, order) | | |
| P2 | 도메인 이벤트, 결제와 알림, 모듈 간 협력 | | |
| P3 | 운영 준비 (보안, 관측성, CI/CD) | | |
| P4 | 성능 기준선과 튜닝 | | |
| P5 | 추출 순서 결정 | | |
| P6 | Gateway와 첫 서비스 추출 (Strangler Fig) | | |
| P7 | DB 분리와 조회 모델 | | |
| P8 | Saga와 이벤트 외부화 (Kafka) | | |
| P9 | 장애 격리 | | |
| P10 | 전후 비교 회고 | | |

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
│       └── kotlin/com/beomsoo/shop/   모듈별 코드 (P1부터 채워진다)
│           ├── member/                회원
│           ├── catalog/               상품 정보
│           ├── inventory/             재고
│           ├── order/                 주문
│           ├── payment/               결제
│           └── notification/          알림
├── docs/
│   ├── adr/                           설계 결정 기록
│   └── posts/                         블로그 초안
└── docker-compose.yml
```

각 모듈의 내부 구조는 다음과 같다 ([ADR-0003](docs/adr/0003-module-internal-structure.md)).

```
<module>/
├── api/             [공개] 다른 모듈이 참조할 수 있는 유일한 패키지
├── domain/          [내부] 순수 Kotlin 도메인 모델
├── application/     [내부] 유스케이스, 포트
└── infrastructure/  [내부] 어댑터 (web, persistence, adapter)
```

모듈 경계와 레이어 규칙은 테스트로 강제한다.

- `ModularityTests`: 모듈 간 의존 방향, 공개 계약(`api`) 외 참조 금지
- `LayerDependencyTests`: 모듈 안쪽 레이어의 의존 방향, 도메인의 순수성

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
