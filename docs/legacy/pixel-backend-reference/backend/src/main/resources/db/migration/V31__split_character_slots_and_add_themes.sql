-- 캐릭터 코스메틱 슬롯 세분화 + 테마 카탈로그.
-- 기존 ownership FK를 보존하기 위해 기존 액세서리 행은 제자리에서 새 slot/sprite_key로 이관한다.

ALTER TABLE character_cosmetics
    ADD COLUMN theme VARCHAR(20) NOT NULL DEFAULT 'BASIC';

ALTER TABLE character_cosmetics DROP CONSTRAINT IF EXISTS character_cosmetics_slot_check;
ALTER TABLE character_cosmetics ADD CONSTRAINT character_cosmetics_slot_check CHECK (
    slot IN ('HAT', 'FACE', 'NECK', 'TOP', 'SHOES', 'BACK', 'HAND', 'EFFECT', 'BACKGROUND')
);

ALTER TABLE character_cosmetics ADD CONSTRAINT character_cosmetics_theme_check CHECK (
    theme IN ('BASIC', 'SEASONAL', 'SPORTS', 'CAREER', 'FANTASY', 'EVENT')
);

UPDATE character_cosmetics SET slot = 'FACE', sprite_key = 'face_sunglasses'
WHERE sprite_key = 'acc_sunglasses';
UPDATE character_cosmetics SET slot = 'NECK', sprite_key = 'neck_scarf'
WHERE sprite_key = 'acc_scarf';
UPDATE character_cosmetics SET slot = 'NECK', sprite_key = 'neck_bowtie'
WHERE sprite_key = 'acc_bowtie';
UPDATE character_cosmetics SET slot = 'BACK', sprite_key = 'back_backpack'
WHERE sprite_key = 'acc_backpack';
UPDATE character_cosmetics SET slot = 'EFFECT', sprite_key = 'effect_swim_tube'
WHERE sprite_key = 'acc_tube';
UPDATE character_cosmetics SET slot = 'FACE', sprite_key = 'face_goggles'
WHERE sprite_key = 'acc_goggles';
UPDATE character_cosmetics SET slot = 'FACE', sprite_key = 'face_ski_goggles'
WHERE sprite_key = 'acc_ski_goggles';
UPDATE character_cosmetics SET slot = 'NECK', sprite_key = 'neck_knit_scarf'
WHERE sprite_key = 'acc_knit_scarf';
UPDATE character_cosmetics SET slot = 'FACE', sprite_key = 'face_headphones'
WHERE sprite_key = 'acc_headphones';
UPDATE character_cosmetics SET slot = 'NECK', sprite_key = 'neck_necklace'
WHERE sprite_key = 'acc_necklace';
UPDATE character_cosmetics SET slot = 'BACK', sprite_key = 'back_fairy_wings'
WHERE sprite_key = 'acc_wings';
UPDATE character_cosmetics SET slot = 'HAND', sprite_key = 'hand_baseball_glove'
WHERE sprite_key = 'acc_baseball_glove';

-- 새 카탈로그 순번과 충돌하지 않도록 기존 순번은 보존 영역으로 이동한다.
UPDATE character_cosmetics
SET display_order = display_order + 1000,
    status = 'DISCONTINUED',
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE character_cosmetic_seed_catalog (
    display_order INTEGER PRIMARY KEY,
    slot VARCHAR(20) NOT NULL,
    theme VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sprite_key VARCHAR(100) NOT NULL UNIQUE,
    price BIGINT NOT NULL CHECK (price >= 0)
);

INSERT INTO character_cosmetic_seed_catalog (
    display_order, slot, theme, name, sprite_key, price
) VALUES
        (1, 'HAT', 'BASIC', '빨간 캡', 'hat_red_cap', 30::BIGINT),
        (2, 'HAT', 'EVENT', '파티모자', 'hat_party', 20::BIGINT),
        (3, 'HAT', 'FANTASY', '왕관', 'hat_crown', 80::BIGINT),
        (4, 'HAT', 'SEASONAL', '밀짚모자', 'hat_straw', 25::BIGINT),
        (5, 'HAT', 'BASIC', '비니', 'hat_beanie', 25::BIGINT),
        (6, 'HAT', 'SEASONAL', '비치 모자', 'hat_beach', 30::BIGINT),
        (7, 'HAT', 'SEASONAL', '방울 비니', 'hat_pom_beanie', 30::BIGINT),
        (8, 'HAT', 'FANTASY', '마법사 모자', 'hat_wizard', 40::BIGINT),
        (9, 'HAT', 'CAREER', '요리사 모자', 'hat_chef', 30::BIGINT),
        (10, 'HAT', 'CAREER', '탐험가 모자', 'hat_safari', 35::BIGINT),
        (11, 'HAT', 'SPORTS', '야구 모자(파랑)', 'hat_baseball_blue', 30::BIGINT),
        (12, 'HAT', 'SPORTS', '야구 모자(빨강)', 'hat_baseball_red', 30::BIGINT),
        (13, 'HAT', 'SPORTS', '야구 모자(남색)', 'hat_baseball_navy', 30::BIGINT),
        (14, 'HAT', 'SPORTS', '야구 모자(초록)', 'hat_baseball_green', 30::BIGINT),
        (15, 'HAT', 'SPORTS', '야구 모자(노랑)', 'hat_baseball_yellow', 30::BIGINT),
        (16, 'HAT', 'CAREER', '우주 헬멧', 'hat_space_helmet', 45::BIGINT),
        (17, 'HAT', 'FANTASY', '기사 투구', 'hat_knight', 40::BIGINT),
        (18, 'HAT', 'FANTASY', '공룡 후드', 'hat_dinosaur', 35::BIGINT),
        (19, 'HAT', 'CAREER', '안전 경찰 모자', 'hat_safety_police', 30::BIGINT),
        (20, 'HAT', 'CAREER', '파일럿 모자', 'hat_pilot', 35::BIGINT),
        (21, 'HAT', 'EVENT', '면사포', 'hat_veil', 30::BIGINT),
        (22, 'FACE', 'BASIC', '선글라스', 'face_sunglasses', 20::BIGINT),
        (23, 'FACE', 'SEASONAL', '물안경', 'face_goggles', 20::BIGINT),
        (24, 'FACE', 'SEASONAL', '스키 고글', 'face_ski_goggles', 30::BIGINT),
        (25, 'FACE', 'BASIC', '헤드폰', 'face_headphones', 40::BIGINT),
        (26, 'NECK', 'SEASONAL', '목도리', 'neck_scarf', 25::BIGINT),
        (27, 'NECK', 'EVENT', '나비넥타이', 'neck_bowtie', 15::BIGINT),
        (28, 'NECK', 'SEASONAL', '니트 목도리', 'neck_knit_scarf', 25::BIGINT),
        (29, 'NECK', 'EVENT', '목걸이', 'neck_necklace', 25::BIGINT),
        (30, 'TOP', 'BASIC', '파란 티셔츠', 'top_blue_tee', 40::BIGINT),
        (31, 'TOP', 'BASIC', '노란 후드', 'top_yellow_hoodie', 50::BIGINT),
        (32, 'TOP', 'BASIC', '초록 조끼', 'top_green_vest', 35::BIGINT),
        (33, 'TOP', 'BASIC', '멜빵바지', 'top_overalls', 45::BIGINT),
        (34, 'TOP', 'SEASONAL', '수영복', 'top_swimsuit', 40::BIGINT),
        (35, 'TOP', 'SEASONAL', '패딩 점퍼', 'top_padding', 55::BIGINT),
        (36, 'TOP', 'EVENT', '정장', 'top_suit', 60::BIGINT),
        (37, 'TOP', 'FANTASY', '마법사 로브', 'top_wizard_robe', 55::BIGINT),
        (38, 'TOP', 'EVENT', '한복', 'top_hanbok', 65::BIGINT),
        (39, 'TOP', 'SPORTS', '축구 유니폼', 'top_soccer', 45::BIGINT),
        (40, 'TOP', 'SPORTS', '야구 유니폼(핀스트라이프)', 'top_baseball_pinstripe', 55::BIGINT),
        (41, 'TOP', 'SPORTS', '야구 유니폼(빨강)', 'top_baseball_red', 50::BIGINT),
        (42, 'TOP', 'SPORTS', '야구 유니폼(파랑)', 'top_baseball_blue', 50::BIGINT),
        (43, 'TOP', 'SPORTS', '야구 유니폼(남색)', 'top_baseball_navy', 50::BIGINT),
        (44, 'TOP', 'SPORTS', '야구 유니폼(원정 회색)', 'top_baseball_gray', 50::BIGINT),
        (45, 'TOP', 'CAREER', '우주복', 'top_spacesuit', 60::BIGINT),
        (46, 'TOP', 'FANTASY', '판타지 갑옷', 'top_fantasy_armor', 55::BIGINT),
        (47, 'TOP', 'FANTASY', '공룡 옷', 'top_dinosaur', 55::BIGINT),
        (48, 'TOP', 'CAREER', '안전 경찰복', 'top_safety_police', 50::BIGINT),
        (49, 'TOP', 'CAREER', '법조인 정장', 'top_legal_professional', 55::BIGINT),
        (50, 'TOP', 'CAREER', '파일럿 제복', 'top_pilot', 55::BIGINT),
        (51, 'TOP', 'EVENT', '웨딩드레스', 'top_wedding_dress', 65::BIGINT),
        (52, 'SHOES', 'BASIC', '운동화', 'shoes_sneakers', 30::BIGINT),
        (53, 'SHOES', 'SEASONAL', '빨간 장화', 'shoes_red_boots', 35::BIGINT),
        (54, 'SHOES', 'SEASONAL', '샌들', 'shoes_sandals', 25::BIGINT),
        (55, 'SHOES', 'SEASONAL', '아쿠아슈즈', 'shoes_aqua', 25::BIGINT),
        (56, 'SHOES', 'SEASONAL', '스노우 부츠', 'shoes_snow_boots', 40::BIGINT),
        (57, 'SHOES', 'CAREER', '우주 부츠', 'shoes_space_boots', 40::BIGINT),
        (58, 'BACK', 'BASIC', '배낭', 'back_backpack', 45::BIGINT),
        (59, 'BACK', 'FANTASY', '요정 날개', 'back_fairy_wings', 50::BIGINT),
        (60, 'BACK', 'FANTASY', '공룡 꼬리', 'back_dinosaur_tail', 30::BIGINT),
        (61, 'HAND', 'SPORTS', '야구 글러브', 'hand_baseball_glove', 30::BIGINT),
        (62, 'HAND', 'FANTASY', '별빛 마법봉', 'hand_magic_wand', 30::BIGINT),
        (63, 'HAND', 'FANTASY', '장난감 검', 'hand_toy_sword', 30::BIGINT),
        (64, 'HAND', 'CAREER', '서류 가방', 'hand_briefcase', 30::BIGINT),
        (65, 'HAND', 'EVENT', '꽃다발', 'hand_bouquet', 25::BIGINT),
        (66, 'EFFECT', 'SEASONAL', '튜브', 'effect_swim_tube', 35::BIGINT),
        (67, 'EFFECT', 'FANTASY', '몽실 구름', 'effect_fluffy_cloud', 45::BIGINT),
        (68, 'BACKGROUND', 'SEASONAL', '봄 벚꽃', 'bg_spring', 45::BIGINT),
        (69, 'BACKGROUND', 'SEASONAL', '여름 바다', 'bg_summer_sea', 50::BIGINT),
        (70, 'BACKGROUND', 'SEASONAL', '가을 단풍', 'bg_autumn', 45::BIGINT),
        (71, 'BACKGROUND', 'SEASONAL', '겨울 눈', 'bg_winter', 50::BIGINT),
        (72, 'BACKGROUND', 'CAREER', '우주', 'bg_space', 55::BIGINT);

INSERT INTO character_cosmetics (
    id, room_id, name, slot, theme, price, sprite_key, anchor,
    display_order, status, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    room.id,
    catalog.name,
    catalog.slot,
    catalog.theme,
    catalog.price,
    catalog.sprite_key,
    CASE catalog.slot
        WHEN 'HAT'        THEN '{"x":0.5,"y":0.05}'::jsonb
        WHEN 'FACE'       THEN '{"x":0.5,"y":0.30}'::jsonb
        WHEN 'NECK'       THEN '{"x":0.5,"y":0.40}'::jsonb
        WHEN 'TOP'        THEN '{"x":0.5,"y":0.60}'::jsonb
        WHEN 'SHOES'      THEN '{"x":0.5,"y":0.92}'::jsonb
        WHEN 'BACK'       THEN '{"x":0.5,"y":0.58}'::jsonb
        WHEN 'HAND'       THEN '{"x":0.78,"y":0.65}'::jsonb
        WHEN 'EFFECT'     THEN '{"x":0.5,"y":0.78}'::jsonb
        WHEN 'BACKGROUND' THEN '{"x":0.5,"y":0.50}'::jsonb
    END,
    catalog.display_order,
    'ON_SALE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM rooms room
CROSS JOIN character_cosmetic_seed_catalog catalog
ON CONFLICT (room_id, sprite_key) DO UPDATE SET
    name = EXCLUDED.name,
    slot = EXCLUDED.slot,
    theme = EXCLUDED.theme,
    price = EXCLUDED.price,
    anchor = EXCLUDED.anchor,
    display_order = EXCLUDED.display_order,
    status = 'ON_SALE',
    updated_at = CURRENT_TIMESTAMP;
