-- 창고 물품은 이름과 테마로 구분한다(be #13). 같은 이름의 물품도 테마마다 따로 두고 수량도 따로 센다.
-- 그래서 이름 유일 제약을 (이름, 테마) 유일로 바꾼다.
ALTER TABLE item
    DROP INDEX uk_item_name,
    ADD CONSTRAINT uk_item_name_theme UNIQUE (name, theme);
