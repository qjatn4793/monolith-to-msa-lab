# ADR-0010. 모듈 간 호출의 예상된 실패는 예외가 아니라 결과값으로 돌려준다

- 상태: 승인
- 날짜: 2026-09-24

## 맥락

주문 서비스는 하나의 트랜잭션 안에서 inventory의 재고 예약을 호출한다. inventory의 서비스도 `@Transactional`이라, 두 메서드는 **같은 트랜잭션에 참여**한다.

이때 inventory가 "재고 부족"을 예외로 던지면 문제가 생긴다. Spring은 `@Transactional` 메서드에서 런타임 예외가 빠져나가는 순간 **트랜잭션 전체를 rollback-only로 표시**한다. 호출한 쪽이 그 예외를 잡고 다른 처리를 이어가더라도, 커밋 시점에 `UnexpectedRollbackException`이 터진다.

`RollbackOnlyLearningTest`로 직접 확인했다.

```
안쪽 트랜잭션에서 던진 예외를 바깥에서 잡아도 커밋할 수 없다       → UnexpectedRollbackException
실패를 결과값으로 돌려주면 바깥 트랜잭션은 정상적으로 커밋된다      → 통과
```

지금 주문 서비스는 재고 부족이면 어차피 주문을 실패시키니 당장은 문제가 없다. 하지만 "재고가 부족하면 가능한 것만 주문" 같은 요구가 생기는 순간 이 함정에 빠진다.

## 결정

- **예상할 수 있는 비즈니스 실패**(재고 부족 등)는 모듈의 공개 API에서 결과값(sealed interface)으로 돌려준다.
  - `InventoryFacade.reserve()` → `ReserveStockResult.Reserved | Rejected`
  - 재고 예약은 전부 검사한 뒤 하나라도 부족하면 **아무것도 바꾸지 않고** `Rejected`를 돌려준다.
- 호출한 쪽(order)이 결과를 보고 자기 모듈의 예외(`OutOfStockException`)로 바꿀지 결정한다.
- 프로그래밍 오류나 인프라 장애처럼 **예상하지 못한 실패**는 그대로 예외로 둔다.

## 결과

- 호출한 쪽이 실패를 처리하는 방식을 스스로 고를 수 있다.
- 공개 API의 시그니처만 보고도 어떤 실패가 가능한지 알 수 있다.
- MSA 전환 후에는 HTTP 응답(예: 409와 부족한 상품 목록)으로 자연스럽게 옮겨진다. 네트워크를 넘으면 트랜잭션 전파 자체가 없어지므로 이 문제는 사라지고, 대신 분산 정합성 문제가 생긴다.
- 비용: 결과 타입이 모듈의 계층마다 하나씩 생긴다 (application의 `ReservationResult`, api의 `ReserveStockResult`, order 포트의 `StockReservation`). 각 계층이 자기 언어를 갖기 위한 비용이다.
