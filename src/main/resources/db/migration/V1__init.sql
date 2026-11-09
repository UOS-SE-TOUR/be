-- 설계산출물 v0.4 4장 엔티티(Member, Tour, TourPrice, TourOption, Booking, Item)의 테이블.
-- 열거형은 이름 문자열(VARCHAR)로 저장한다. 시각(applied_at)은 UTC로 저장한다.

CREATE TABLE member (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    login_id    VARCHAR(50)  NOT NULL,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    address     VARCHAR(255) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    member_type VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_login_id UNIQUE (login_id)
);

CREATE TABLE tour (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    theme          VARCHAR(20) NOT NULL,
    departure_date DATE        NOT NULL,
    duration       INT         NOT NULL,
    capacity       INT         NOT NULL,
    applied_count  INT         NOT NULL,
    status         VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_tour_theme_departure_date (theme, departure_date)
);

CREATE TABLE tour_price (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    tour_id BIGINT      NOT NULL,
    level   VARCHAR(20) NOT NULL,
    price   BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tour_price_tour_level UNIQUE (tour_id, level),
    CONSTRAINT fk_tour_price_tour FOREIGN KEY (tour_id) REFERENCES tour (id)
);

CREATE TABLE tour_option (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    tour_id     BIGINT       NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    unit_price  BIGINT       NOT NULL,
    default_for VARCHAR(20),
    PRIMARY KEY (id),
    CONSTRAINT fk_tour_option_tour FOREIGN KEY (tour_id) REFERENCES tour (id)
);

CREATE TABLE booking (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    tour_id     BIGINT      NOT NULL,
    level       VARCHAR(20) NOT NULL,
    headcount   INT         NOT NULL,
    total_price BIGINT      NOT NULL,
    discount    BIGINT      NOT NULL,
    applied_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_booking_tour FOREIGN KEY (tour_id) REFERENCES tour (id)
);

-- 신청에서 고른 옵션(Booking.options)
CREATE TABLE booking_option (
    booking_id     BIGINT NOT NULL,
    tour_option_id BIGINT NOT NULL,
    PRIMARY KEY (booking_id, tour_option_id),
    CONSTRAINT fk_booking_option_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_booking_option_tour_option FOREIGN KEY (tour_option_id) REFERENCES tour_option (id)
);

CREATE TABLE item (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(100) NOT NULL,
    theme    VARCHAR(20)  NOT NULL,
    quantity INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_name UNIQUE (name)
);
