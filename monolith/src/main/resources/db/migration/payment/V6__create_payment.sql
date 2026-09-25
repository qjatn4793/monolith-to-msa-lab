-- payment 모듈 소유 테이블 (ADR-0009)
-- order_id는 order 모듈의 ID지만 모듈 경계를 넘는 FK는 걸지 않는다.
create table payment
(
    id                binary(16)   not null,
    order_id          binary(16)   not null,
    amount            bigint       not null,
    status            varchar(20)  not null,
    pg_transaction_id varchar(100) null,
    failure_reason    varchar(255) null,
    requested_at      datetime(6)  not null,
    completed_at      datetime(6)  null,
    version           bigint       not null,
    primary key (id),
    index idx_payment_order_id (order_id)
);
