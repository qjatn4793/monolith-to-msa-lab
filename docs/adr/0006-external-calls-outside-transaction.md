# ADR-0006. 외부 시스템 호출은 DB 트랜잭션 밖에서 한다

- 상태: 승인 (P2에서 구현)
- 날짜: 2026-09-24

## 맥락

주문 흐름에는 외부 PG 호출이 들어간다. 이 호출을 `@Transactional` 안에 두면 다음 문제가 생긴다.

- PG 응답을 기다리는 동안 **DB 커넥션을 붙잡고** 있는다. PG가 느려지면 커넥션 풀이 고갈되고, 결제와 무관한 API까지 멈춘다.
- PG 결제는 성공했는데 DB 커밋이 실패하면, **돈은 빠졌는데 주문은 없는** 상태가 된다. DB 롤백으로 외부 호출은 되돌릴 수 없다.

## 결정

주문 흐름을 여러 개의 짧은 트랜잭션으로 나눈다.

```
[TX1] 주문 생성 (PENDING) + 재고 예약
      ↓
      PG 결제 요청 (트랜잭션 밖, 멱등성 키 포함)
      ↓
[TX2] 성공 → 주문 확정, 재고 차감 확정
      실패 → 주문 취소, 재고 예약 해제 (보상)
```

### P2 구현

- 주문(`OrderCommandService.place`)과 결제(`PaymentService.pay`) 모두 `@Transactional` 대신 `TransactionOperations`로 트랜잭션 경계를 코드에 드러냈다. 한 메서드 안에서 `[TX1] → 외부 호출 → [TX2]`가 보인다.
- 결제는 PG를 부르기 전에 `REQUESTED`로 먼저 기록한다. PG 호출 중에 프로세스가 죽어도 "결제를 시도했다"는 흔적이 남는다.
- **실행 시점에 강제한다.** `PgClientAdapter`는 PG를 호출하기 직전에 `TransactionSynchronizationManager.isActualTransactionActive()`를 검사하고, 트랜잭션이 열려 있으면 `IllegalStateException`을 던진다. `PaymentTransactionGuardTest`로 확인했다.
- PG 호출에는 연결 타임아웃 1초, 읽기 타임아웃 3초를 둔다.

## 결과

- PG 지연이 DB 커넥션 풀로 번지지 않는다.
- 모놀리식 안에서 이미 작은 Saga 형태가 된다. MSA로 전환하면 단계 사이의 경계가 서비스 경계로 바뀔 뿐, 흐름은 같다.
- 비용: 주문 상태가 늘어나고(PENDING, CONFIRMED, CANCELLED ...), 중간에 프로세스가 죽었을 때 복구 로직이 필요하다.
- 성능 편(P4)에서 "트랜잭션 안에서 외부 호출"과 비교 실험을 한다.
- TX1과 TX2 사이, 그리고 PG 타임아웃에서 생기는 문제는 ADR-0012에 정리했다.
