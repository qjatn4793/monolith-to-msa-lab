# ADR-0009. 테이블은 모듈이 소유하고, 모듈 경계를 넘는 FK와 JOIN은 쓰지 않는다

- 상태: 승인
- 날짜: 2026-09-24

## 맥락

DB는 하나다(ADR-0001). 같은 DB 안에서는 어떤 코드든 어떤 테이블이든 읽을 수 있다. 이걸 막지 않으면 `orders JOIN member JOIN product` 같은 쿼리가 생기고, 나중에 DB를 나눌 때 가장 큰 걸림돌이 된다.

## 결정

| 모듈 | 소유 테이블 | 마이그레이션 위치 |
|---|---|---|
| member | `member` | `db/migration/member/` |
| catalog | `product` | `db/migration/catalog/` |
| inventory | `stock` | `db/migration/inventory/` |
| order | `orders`, `order_line` | `db/migration/order/` |
| payment | `payment` | `db/migration/payment/` |
| notification | `notification` | `db/migration/notification/` |
| (Spring Modulith) | `EVENT_PUBLICATION` | `db/migration/shared/` |

- 모듈은 **자기 테이블만** 읽고 쓴다. 다른 모듈의 데이터가 필요하면 그 모듈의 `api`를 호출한다.
- **모듈 경계를 넘는 FK는 걸지 않는다.** `orders.member_id`, `stock.product_id`는 다른 모듈의 ID지만 FK가 없다.
- 같은 모듈 안의 FK는 건다 (`order_line.order_id → orders.id`).
- Flyway 마이그레이션은 모듈별 폴더에 둔다. Flyway는 하위 폴더를 재귀로 읽고, 버전 번호는 전역에서 유일해야 한다.

## 결과

- DB를 분리할 때 옮길 테이블과 마이그레이션이 모듈 단위로 이미 나뉘어 있다.
- FK가 없으므로 "없는 회원 ID로 주문"은 DB가 막아주지 않는다. 애플리케이션(주문 서비스가 member api로 확인)이 막는다.
- 비용: 모듈을 넘나드는 목록 화면(예: 주문 목록에 회원 이름 표시)은 JOIN 대신 API를 여러 번 호출해 조합해야 한다. 성능 문제가 생기면 P4에서 조회 모델로 해결한다.
- 다시 볼 것: 테이블 소유 규칙을 테스트로 강제할 수 있는지 (예: 엔티티 패키지와 테이블 이름 매핑 검사).
