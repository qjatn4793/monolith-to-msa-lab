-- inventory 모듈 소유 테이블 (ADR-0009)
-- product_id는 catalog의 상품 ID지만, 모듈 경계를 넘는 FK는 걸지 않는다.
create table stock
(
    product_id binary(16) not null,
    available  int        not null,
    reserved   int        not null,
    version    bigint     not null,
    primary key (product_id),
    constraint ck_stock_available check (available >= 0),
    constraint ck_stock_reserved check (reserved >= 0)
);
