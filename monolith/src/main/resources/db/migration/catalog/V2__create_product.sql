-- catalog 모듈 소유 테이블 (ADR-0009)
create table product
(
    id            binary(16)    not null,
    name          varchar(100)  not null,
    price         bigint        not null,
    description   varchar(1000) null,
    status        varchar(20)   not null,
    registered_at datetime(6)   not null,
    version       bigint        not null,
    primary key (id)
);
