-- 창고 물품 수량이 바뀐 기록이다: 등록, 수량 더하기, 상품 확정 때 줄임, 늦은 취소로 되돌림. 1인당 개수 바꾸기는 수량을 바꾸지 않아 남기지 않는다.
-- 물품을 지워도 기록은 남도록 외래 키를 두지 않고, 물품 이름·테마와 상품 이름을 그때 값으로 적는다.
-- 늦은 취소로 되돌릴 양은 그 상품의 기록을 더해 정하므로 상품 색인을 둔다. 이 표가 생기기 전에 줄인 수량은 기록이 없어 되돌리지 않는다.
CREATE TABLE item_history
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    item_id        BIGINT       NOT NULL,
    item_name      VARCHAR(100) NOT NULL,
    theme          VARCHAR(20)  NOT NULL,
    reason         VARCHAR(20)  NOT NULL,
    tour_id        BIGINT       NULL,
    tour_title     VARCHAR(100) NULL,
    change_amount  INT          NOT NULL,
    quantity_after INT          NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_item_history_tour (tour_id)
);
