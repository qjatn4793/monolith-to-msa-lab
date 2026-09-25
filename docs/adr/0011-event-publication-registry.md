# ADR-0011. 모듈 간 비동기 협력은 Spring Modulith 이벤트 발행 저장소로 한다

- 상태: 승인
- 날짜: 2026-09-25

## 맥락

주문이 확정되면 알림을 보내야 한다. 알림은 주문의 일부가 아니다. 알림 서버가 죽었다고 주문이 실패하면 안 된다. 그래서 동기 호출이 아니라 이벤트로 알린다.

그런데 단순한 Spring 이벤트(`@TransactionalEventListener`)는 **메모리 안에서만** 전달된다. 리스너가 실패하거나, 전달 직전에 프로세스가 죽으면 이벤트는 사라진다. 주문은 확정됐는데 알림은 영영 안 가는 상태가 된다.

이걸 막는 전형적인 방법이 **트랜잭셔널 아웃박스**다. 상태 변경과 "보낼 이벤트"를 같은 DB 트랜잭션에 기록하고, 전달은 그 기록을 보고 나중에 한다.

## 결정

Spring Modulith의 **이벤트 발행 저장소(Event Publication Registry)**를 쓴다.

```
[주문 확정 트랜잭션]
  UPDATE orders SET status='CONFIRMED'
  INSERT INTO EVENT_PUBLICATION (리스너, 이벤트 JSON, ...)   ← 같은 트랜잭션
커밋
  └─▶ (다른 스레드, 새 트랜잭션) 알림 리스너 실행
        성공 → EVENT_PUBLICATION.COMPLETION_DATE 채움, STATUS='COMPLETED'
        실패 → STATUS='FAILED'로 남음. 재제출하거나 재시작 시 다시 전달
```

- 발행: order의 `OrderEventPublisherAdapter`가 도메인 이벤트를 통합 이벤트(`order.api.OrderConfirmedEvent` 등)로 바꿔 `ApplicationEventPublisher`로 발행한다. 반드시 주문 상태를 바꾸는 트랜잭션 **안에서** 발행한다.
- 구독: notification의 `OrderEventListener`가 `@ApplicationModuleListener`로 받는다. 이 어노테이션은 `@Async` + `@Transactional(REQUIRES_NEW)` + `@TransactionalEventListener`다.
- 저장소: `spring-modulith-starter-jdbc`. 테이블은 Flyway로 만든다 (`shared/V8__create_event_publication.sql`).
- 재시작 시 미완료 이벤트를 다시 발행한다 (`spring.modulith.events.republish-outstanding-events-on-restart: true`).
- 이벤트는 **최소 한 번** 전달되므로, 구독자는 멱등해야 한다. notification은 `(order_id, type)` 유니크 제약과 사전 검사로 중복 발송을 막는다.

### 검증

`EventPublicationRegistryTest`에서 알림 발송을 일부러 실패시켰다.

```
주문 응답:   status=CONFIRMED  (알림 장애와 무관하게 주문은 확정됨)
장애 중:     STATUS=FAILED,    COMPLETION_ATTEMPTS=1, COMPLETION_DATE=null
재제출 후:   STATUS=COMPLETED, COMPLETION_ATTEMPTS=2, COMPLETION_DATE=2026-09-25 08:07:06.443
```

### 함정: 테이블이 두 개 생겼다

처음에는 Flyway로 소문자 `event_publication` 테이블을 만들었다. 그런데 테스트에서 이 테이블은 계속 비어 있었다. 원인은 두 가지였다.

1. Modulith의 JDBC 스키마 자동 생성(`spring.modulith.events.jdbc.schema-initialization.enabled`)은 `@ConditionalOnProperty(..., matchIfMissing = true)`다. **값을 지정하지 않으면 켜진다.** 설정 메타데이터에는 기본값이 `false`로 표시되어 있어서 꺼져 있는 줄 알았다.
2. Modulith는 대문자 `EVENT_PUBLICATION`으로 테이블을 만들고 쿼리한다. 리눅스 MySQL(`lower_case_table_names=0`)은 테이블 이름의 대소문자를 구분한다.

그래서 Flyway 다음에 Modulith가 `CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION`으로 **두 번째 테이블**을 만들어 거기에 기록하고 있었다. 에러는 하나도 없었다. 재처리 테스트도 통과했다. Modulith는 자기 테이블로 잘 동작했기 때문이다.

조치:
- Flyway 마이그레이션의 테이블 이름을 대문자 `EVENT_PUBLICATION`으로 바꿨다.
- `schema-initialization.enabled: false`를 **명시**했다.
- `EventPublicationRegistryTest`에 "이벤트 발행 테이블은 Flyway가 만든 하나뿐이다" 테스트를 추가했다. 원래 버그 상태로 되돌려 이 테스트가 `["EVENT_PUBLICATION", "event_publication"]`을 보고하며 실패하는 것을 확인했다.

## 결과

- 알림 장애가 주문으로 번지지 않는다. 전달에 실패한 이벤트는 유실되지 않고 남는다.
- MSA 전환 시 같은 이벤트를 Kafka로 외부화할 수 있다 (Modulith의 이벤트 외부화 기능). 통합 이벤트가 이미 공개 계약으로 분리되어 있다.
- 비용: 이벤트마다 `EVENT_PUBLICATION`에 행이 쌓인다. 완료된 행을 정리하는 정책(`completion-mode`, 보관 기간)이 필요하다. P3에서 운영 관점으로 다시 본다.
- 비용: 이벤트 JSON이 테이블에 저장되므로 통합 이벤트의 필드를 바꾸면 미완료 이벤트를 역직렬화하지 못할 수 있다. 통합 이벤트는 필드 추가만 한다.
- 다시 볼 것: 여러 인스턴스로 띄우면 재시작 시 재발행이 중복될 수 있다. 구독자 멱등성이 그래서 중요하다.
