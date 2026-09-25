-- 주문 취소 사유 (결제 실패, 요청). 취소되지 않은 주문은 null
alter table orders
    add column cancel_reason varchar(30) null after status;
