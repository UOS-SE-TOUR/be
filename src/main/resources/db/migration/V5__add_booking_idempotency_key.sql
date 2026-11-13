-- 신청에 신청 키(요청 헤더 Idempotency-Key, UUID 36자)를 함께 저장한다(be #68). 같은 회원이 같은 신청 키로 다시 신청하면
-- 새로 신청하지 않고 첫 신청의 결과를 돌려주고, 같은 신청 키로 동시에 와도 이 유일 제약으로 신청이 하나만 남는다.
-- 신청 키 없이 한 신청과 이 마이그레이션 전의 신청은 NULL이다. MySQL 유일 제약은 NULL끼리는 겹쳐도 된다.
ALTER TABLE booking
    ADD COLUMN idempotency_key VARCHAR(36) NULL,
    ADD CONSTRAINT uk_booking_member_idempotency_key UNIQUE (member_id, idempotency_key);
