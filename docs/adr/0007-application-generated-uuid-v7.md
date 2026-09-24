# ADR-0007. ID는 애플리케이션이 UUID v7로 만든다

- 상태: 승인
- 날짜: 2026-09-24

## 맥락

ID를 만드는 방법은 크게 두 가지다.

| | DB auto increment | 애플리케이션이 생성 (UUID 등) |
|---|---|---|
| ID를 아는 시점 | INSERT 이후 | 객체를 만드는 순간 |
| 도메인 모델 | 저장 전에는 ID가 없어 `id: Long?`처럼 nullable이 된다 | 항상 ID가 있다 |
| 여러 DB/서비스 | 서비스마다 번호가 겹친다 | 전역에서 유일하다 |
| 인덱스 성능 | 순차 증가라 좋다 | 랜덤 UUID(v4)는 B-Tree 중간에 끼어들어 나쁘다 |

MSA 전환을 전제로 하면 애플리케이션 생성이 유리하다. 이벤트를 발행할 때 저장 전이라도 ID를 담을 수 있고, DB를 나눠도 ID가 충돌하지 않는다. 문제는 인덱스 성능이다.

## 결정

- 모든 애그리거트 ID는 **UUID v7**(RFC 9562)로 애플리케이션이 만든다. Kotlin 표준 라이브러리의 `Uuid.generateV7()`을 쓴다 (`shared.domain.newId()`).
- UUID v7은 앞 48비트가 밀리초 타임스탬프라 **생성 순서대로 정렬**된다. auto increment처럼 인덱스 끝에 추가되므로 v4의 인덱스 단편화 문제가 없다.
- MySQL에는 `binary(16)`으로 저장한다. 문자열(`char(36)`)보다 절반 이하 크기다.
- 도메인에서는 모듈별 값 클래스(`MemberId`, `ProductId`, `OrderId`)로 감싸서 서로 다른 ID를 섞어 쓰는 실수를 컴파일 단계에서 막는다.
- **다른 모듈의 ID는 `UUID`로 들고 있는다.** 다른 모듈의 값 클래스는 내부 타입이라 참조할 수 없다 (ADR-0003).

## 결과

- 저장 전에도 ID가 있으므로 도메인 모델에 nullable ID가 없다.
- PK 역순 정렬이 곧 최신순이다. 목록 조회에서 `ORDER BY id DESC`를 쓴다.
- 비용: DB 콘솔에서 ID를 읽으려면 `BIN_TO_UUID(id)`가 필요하다.
- 비용: `generateV7()`은 Kotlin에서 아직 실험적 API라 `@OptIn`이 필요하다 (`build.gradle.kts`에서 전역으로 켰다).
- 다시 볼 것: auto increment `bigint`와 비교해 삽입 성능이 실제로 어떤지 P4에서 측정한다.
