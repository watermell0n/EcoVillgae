-- 캐릭터 코스메틱 소비 모듈 (기획: docs/plans/2026-07-29-character-cosmetic-module-plan.md)
-- 돈은 지갑 -> CHARACTER_EXPENSE(소비 싱크)로 한 방향으로만 이동한다(환급 없음).

-- 1) 소비 싱크 계정 CHARACTER_EXPENSE 추가 (SHOP_REVENUE처럼 지연 생성되므로 CHECK만 확장)
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_account_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_account_type_check CHECK (
    account_type IN (
        'STUDENT_WALLET', 'STUDENT_DEPOSIT', 'STUDENT_INVESTMENT',
        'CLASS_FUND', 'SHOP_REVENUE', 'CLASS_EXPENSE', 'CHARACTER_EXPENSE', 'SYSTEM_TREASURY'
    )
);

-- 2) 학생 캐릭터 (학생당 1개, 캐릭터 화면 첫 진입 시 무료 지연 생성)
CREATE TABLE student_characters (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    student_id UUID NOT NULL REFERENCES students(id),
    name VARCHAR(50) NOT NULL DEFAULT '이코',
    equipped JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, student_id)
);

-- 3) 코스메틱 카탈로그 (방 스코프, 고정 데모 시드)
CREATE TABLE character_cosmetics (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    name VARCHAR(100) NOT NULL,
    slot VARCHAR(20) NOT NULL CHECK (slot IN ('HAT', 'TOP', 'SHOES', 'ACCESSORY', 'BACKGROUND')),
    price BIGINT NOT NULL CHECK (price >= 0),
    sprite_key VARCHAR(100) NOT NULL,
    anchor JSONB NOT NULL DEFAULT '{}'::jsonb,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE' CHECK (status IN ('ON_SALE', 'DISCONTINUED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, sprite_key),
    UNIQUE (room_id, display_order)
);

-- 4) 소유 = 소비 이력 (원장 거래에 연결)
CREATE TABLE character_cosmetic_ownership (
    id UUID PRIMARY KEY,
    character_id UUID NOT NULL REFERENCES student_characters(id),
    cosmetic_id UUID NOT NULL REFERENCES character_cosmetics(id),
    transaction_id UUID NOT NULL UNIQUE REFERENCES financial_transactions(id),
    price_paid BIGINT NOT NULL CHECK (price_paid >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, cosmetic_id)
);

CREATE INDEX idx_student_characters_room ON student_characters(room_id);
CREATE INDEX idx_character_cosmetics_room_order ON character_cosmetics(room_id, display_order);
CREATE INDEX idx_character_cosmetic_ownership_character ON character_cosmetic_ownership(character_id);

-- 5) 코스메틱 카탈로그 시드 (49종) — 기존 상점 상품 시드(V18)와 동일하게 모든 기존 방에 INSERT.
--    로컬/데모 방은 Flyway 이후 생성되므로 LocalDemoDataInitializer 에서 동일 카탈로그를 별도 시드한다.
WITH catalog(display_order, slot, name, sprite_key, price) AS (
    VALUES
        (1,  'HAT', '빨간 캡',      'hat_red_cap',      30::BIGINT),
        (2,  'HAT', '파티모자',     'hat_party',        20::BIGINT),
        (3,  'HAT', '왕관',         'hat_crown',        80::BIGINT),
        (4,  'HAT', '밀짚모자',     'hat_straw',        25::BIGINT),
        (5,  'HAT', '비니',         'hat_beanie',       25::BIGINT),
        (6,  'HAT', '비치 모자',    'hat_beach',        30::BIGINT),
        (7,  'HAT', '방울 비니',    'hat_pom_beanie',   30::BIGINT),
        (8,  'HAT', '마법사 모자',  'hat_wizard',       40::BIGINT),
        (9,  'HAT', '요리사 모자',  'hat_chef',         30::BIGINT),
        (10, 'HAT', '탐험가 모자',  'hat_safari',       35::BIGINT),
        (11, 'TOP', '파란 티셔츠',  'top_blue_tee',     40::BIGINT),
        (12, 'TOP', '노란 후드',    'top_yellow_hoodie', 50::BIGINT),
        (13, 'TOP', '초록 조끼',    'top_green_vest',   35::BIGINT),
        (14, 'TOP', '멜빵바지',     'top_overalls',     45::BIGINT),
        (15, 'TOP', '수영복',       'top_swimsuit',     40::BIGINT),
        (16, 'TOP', '패딩 점퍼',    'top_padding',      55::BIGINT),
        (17, 'TOP', '정장',         'top_suit',         60::BIGINT),
        (18, 'TOP', '마법사 로브',  'top_wizard_robe',  55::BIGINT),
        (19, 'TOP', '한복',         'top_hanbok',       65::BIGINT),
        (20, 'TOP', '축구 유니폼',  'top_soccer',       45::BIGINT),
        (21, 'SHOES', '운동화',     'shoes_sneakers',   30::BIGINT),
        (22, 'SHOES', '빨간 장화',  'shoes_red_boots',  35::BIGINT),
        (23, 'SHOES', '샌들',       'shoes_sandals',    25::BIGINT),
        (24, 'SHOES', '아쿠아슈즈', 'shoes_aqua',       25::BIGINT),
        (25, 'SHOES', '스노우 부츠', 'shoes_snow_boots', 40::BIGINT),
        (26, 'ACCESSORY', '선글라스',   'acc_sunglasses',  20::BIGINT),
        (27, 'ACCESSORY', '목도리',     'acc_scarf',       25::BIGINT),
        (28, 'ACCESSORY', '나비넥타이', 'acc_bowtie',      15::BIGINT),
        (29, 'ACCESSORY', '배낭',       'acc_backpack',    45::BIGINT),
        (30, 'ACCESSORY', '튜브',       'acc_tube',        35::BIGINT),
        (31, 'ACCESSORY', '물안경',     'acc_goggles',     20::BIGINT),
        (32, 'ACCESSORY', '스키 고글',  'acc_ski_goggles', 30::BIGINT),
        (33, 'ACCESSORY', '니트 목도리', 'acc_knit_scarf', 25::BIGINT),
        (34, 'ACCESSORY', '헤드폰',     'acc_headphones',  40::BIGINT),
        (35, 'ACCESSORY', '목걸이',     'acc_necklace',    25::BIGINT),
        (36, 'ACCESSORY', '요정 날개',  'acc_wings',       50::BIGINT),
        (37, 'BACKGROUND', '밤하늘',    'bg_night',        40::BIGINT),
        (38, 'BACKGROUND', '공원',      'bg_park',         45::BIGINT),
        (39, 'BACKGROUND', '무지개',    'bg_rainbow',      50::BIGINT),
        (40, 'BACKGROUND', '봄 벚꽃',   'bg_spring',       45::BIGINT),
        (41, 'BACKGROUND', '여름 바다', 'bg_summer_sea',   50::BIGINT),
        (42, 'BACKGROUND', '가을 단풍', 'bg_autumn',       45::BIGINT),
        (43, 'BACKGROUND', '겨울 눈',   'bg_winter',       50::BIGINT),
        (44, 'BACKGROUND', '우주',      'bg_space',        55::BIGINT),
        (45, 'BACKGROUND', '도시 야경', 'bg_city',         45::BIGINT),
        (46, 'BACKGROUND', '숲',        'bg_forest',       40::BIGINT),
        (47, 'BACKGROUND', '사막',      'bg_desert',       40::BIGINT),
        (48, 'BACKGROUND', '무대',      'bg_stage',        55::BIGINT),
        (49, 'BACKGROUND', '바닷속',    'bg_underwater',   50::BIGINT)
)
INSERT INTO character_cosmetics (
    id, room_id, name, slot, price, sprite_key, anchor, display_order, status, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    room.id,
    catalog.name,
    catalog.slot,
    catalog.price,
    catalog.sprite_key,
    CASE catalog.slot
        WHEN 'HAT'        THEN '{"x":0.5,"y":0.05}'::jsonb
        WHEN 'ACCESSORY'  THEN '{"x":0.5,"y":0.35}'::jsonb
        WHEN 'TOP'        THEN '{"x":0.5,"y":0.60}'::jsonb
        WHEN 'SHOES'      THEN '{"x":0.5,"y":0.92}'::jsonb
        WHEN 'BACKGROUND' THEN '{"x":0.5,"y":0.50}'::jsonb
    END,
    catalog.display_order,
    'ON_SALE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM rooms room
CROSS JOIN catalog
ON CONFLICT DO NOTHING;
