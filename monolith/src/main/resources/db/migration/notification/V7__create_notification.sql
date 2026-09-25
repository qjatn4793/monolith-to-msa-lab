-- notification 모듈 소유 테이블 (ADR-0009)
-- 같은 주문에 같은 종류의 알림은 한 번만 보낸다. 이벤트가 두 번 전달돼도 중복 발송을 막는 마지막 방어선이다.
create table notification
(
    id        binary(16)    not null,
    member_id binary(16)    not null,
    order_id  binary(16)    not null,
    type      varchar(30)   not null,
    recipient varchar(255)  not null,
    message   varchar(1000) not null,
    sent_at   datetime(6)   not null,
    version   bigint        not null,
    primary key (id),
    constraint uk_notification_order_type unique (order_id, type),
    index idx_notification_member_id (member_id, id)
);
