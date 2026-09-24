-- member 모듈 소유 테이블. 다른 모듈은 이 테이블을 직접 읽거나 JOIN 하지 않는다 (ADR-0009).
create table member
(
    id        binary(16)   not null,
    email     varchar(255) not null,
    name      varchar(50)  not null,
    status    varchar(20)  not null,
    joined_at datetime(6)  not null,
    version   bigint       not null,
    primary key (id),
    constraint uk_member_email unique (email)
);
