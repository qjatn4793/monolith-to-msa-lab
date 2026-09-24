-- order 모듈 소유 테이블 (ADR-0009)
-- member_id, product_id는 다른 모듈의 ID지만 모듈 경계를 넘는 FK는 걸지 않는다.
-- order_line → orders 처럼 같은 모듈 안의 FK는 건다.
create table orders
(
    id           binary(16)  not null,
    member_id    binary(16)  not null,
    status       varchar(20) not null,
    total_amount bigint      not null,
    ordered_at   datetime(6) not null,
    version      bigint      not null,
    primary key (id),
    index idx_orders_member_id (member_id, id)
);

create table order_line
(
    order_id     binary(16)   not null,
    line_no      int          not null,
    product_id   binary(16)   not null,
    product_name varchar(100) not null,
    unit_price   bigint       not null,
    quantity     int          not null,
    primary key (order_id, line_no),
    constraint fk_order_line_order foreign key (order_id) references orders (id)
);
