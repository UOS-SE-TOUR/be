-- 문자셋·정렬 규칙을 MySQL 서버 설정에 맡기지 않고 여기서 정한다(be #32). V1·V2는 규칙을 적지 않아 서버 기본값을 따랐다.
-- utf8mb4_0900_ai_ci는 MySQL 8.4의 기본값이라 지금까지의 동작 그대로다 — 대소문자·악센트·전각과 반각을 가리지 않는다.
-- 그래서 회원 아이디 hong이 있으면 HONG·hóng으로는 가입할 수 없고 그 꼴로 로그인하면 hong으로 들어가며,
-- 창고의 'Golf Ball'과 'golf ball'은 같은 물품이다. 서버 기본값이 다른 DB(utf8mb4_bin 등)에서도 이렇게 동작하게 한다.

-- 이 DB의 기본값. 뒤에 더하는 테이블은 CREATE TABLE에 규칙을 적지 않아도 이 값을 물려받는다
ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- V1·V2가 만든 테이블. 테이블 기본값과 문자열 칸을 함께 바꾼다
ALTER TABLE member CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE tour CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE tour_price CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE tour_option CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE booking CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE booking_option CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE item CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
