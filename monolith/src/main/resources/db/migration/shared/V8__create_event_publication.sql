-- Spring Modulith 이벤트 발행 저장소 (ADR-0011)
-- spring-modulith-events-jdbc 2.1.1의 schemas/v2/schema-mysql.sql과 같은 구조다.
--
-- 테이블 이름은 반드시 대문자여야 한다. Modulith는 EVENT_PUBLICATION이라는 이름으로 쿼리하는데,
-- 리눅스의 MySQL(lower_case_table_names=0)은 테이블 이름의 대소문자를 구분한다.
-- 처음에 소문자로 만들었다가, Modulith가 자기 스키마 생성 기능으로 대문자 테이블을 따로 만들어 쓰고
-- 이 마이그레이션의 테이블은 한 번도 쓰이지 않는 것을 테스트로 발견했다.
--
-- 이벤트를 발행한 트랜잭션 안에서 "이 이벤트를 이 리스너에게 전달해야 한다"는 행이 생기고,
-- 리스너가 성공하면 COMPLETION_DATE가 채워진다. 비어 있는 행은 아직 전달되지 않은 이벤트다.
CREATE TABLE EVENT_PUBLICATION
(
    ID                     VARCHAR(36)   NOT NULL,
    LISTENER_ID            VARCHAR(512)  NOT NULL,
    EVENT_TYPE             VARCHAR(512)  NOT NULL,
    SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
    PUBLICATION_DATE       TIMESTAMP(6)  NOT NULL,
    COMPLETION_DATE        TIMESTAMP(6)  NULL DEFAULT NULL,
    STATUS                 VARCHAR(20),
    COMPLETION_ATTEMPTS    INT,
    LAST_RESUBMISSION_DATE TIMESTAMP(6)  NULL DEFAULT NULL,
    PRIMARY KEY (ID),
    INDEX EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX (COMPLETION_DATE)
);
