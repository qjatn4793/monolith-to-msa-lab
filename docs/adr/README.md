# Architecture Decision Records

설계 결정과 그 이유를 기록한다. 결정이 바뀌면 기존 ADR을 고치지 않고, 새 ADR을 쓰고 기존 ADR의 상태를 `대체됨`으로 바꾼다.
"왜 이렇게 했는지"가 남아 있어야 나중에 MSA로 전환하면서 무엇이 틀렸는지 비교할 수 있다.

| 번호 | 제목 | 상태 | 단계 |
|---|---|---|---|
| [0001](0001-modular-monolith-first.md) | 모듈러 모놀리식으로 시작한다 | 승인 | P0 |
| [0002](0002-bounded-contexts.md) | 바운디드 컨텍스트를 여섯 개 모듈로 나눈다 | 승인 | P0 |
| [0003](0003-module-internal-structure.md) | 모듈 내부는 api / domain / application / infrastructure로 나눈다 | 승인 | P0 |
| [0004](0004-separate-domain-and-jpa-model.md) | 도메인 모델과 JPA 엔티티를 분리한다 | 승인 | P0 |
| [0005](0005-inter-module-communication.md) | 모듈 간 통신은 공개 API와 이벤트로만 한다 | 승인 | P0 |
| [0006](0006-external-calls-outside-transaction.md) | 외부 시스템 호출은 DB 트랜잭션 밖에서 한다 | 제안 | P2에서 확정 |
| [0007](0007-application-generated-uuid-v7.md) | ID는 애플리케이션이 UUID v7로 만든다 | 승인 | P1 |
| [0008](0008-shared-kernel.md) | 공유 커널(shared)은 최소한으로 둔다 | 승인 | P1 |
| [0009](0009-table-ownership.md) | 테이블은 모듈이 소유하고, 경계를 넘는 FK와 JOIN은 쓰지 않는다 | 승인 | P1 |
| [0010](0010-cross-module-failures-as-results.md) | 모듈 간 호출의 예상된 실패는 결과값으로 돌려준다 | 승인 | P1 |

## 템플릿

```markdown
# ADR-NNNN. 제목

- 상태: 제안 | 승인 | 대체됨 (ADR-XXXX)
- 날짜: YYYY-MM-DD

## 맥락
어떤 문제가 있었고, 어떤 제약이 있었나.

## 결정
무엇을 하기로 했나.

## 결과
좋아지는 것, 치르는 비용, 나중에 다시 봐야 할 것.
```
