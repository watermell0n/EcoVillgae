ALTER TABLE room_rules
    ALTER COLUMN role_income SET DEFAULT 150,
    ALTER COLUMN day1_participation_reward SET DEFAULT 0,
    ALTER COLUMN day1_correct_reward SET DEFAULT 5,
    ALTER COLUMN day2_participation_reward SET DEFAULT 0,
    ALTER COLUMN day2_correct_reward SET DEFAULT 5,
    ALTER COLUMN income_tax_bps SET DEFAULT 1000;

UPDATE room_rules
SET role_income = 150,
    day1_participation_reward = 0,
    day1_correct_reward = 5,
    day2_participation_reward = 0,
    day2_correct_reward = 5,
    income_tax_bps = 1000,
    updated_at = CURRENT_TIMESTAMP;

WITH catalog(display_order, name, description, price, stock) AS (
    VALUES
        (1, '말랑카우 빠삐코', '달콤하고 시원하게 즐기는 간식 세트예요.', 35::BIGINT, 38),
        (2, '쿠크다스케이크', '부드럽고 달콤한 케이크 간식이에요.', 65::BIGINT, 10),
        (3, '마이쭈 딸기', '쫀득하게 즐기는 딸기맛 간식이에요.', 45::BIGINT, 18),
        (4, '포도 알맹이', '탱글탱글한 포도맛 젤리 간식이에요.', 80::BIGINT, 6),
        (5, '카피바라 지우개', '귀여운 카피바라 모양 지우개예요.', 120::BIGINT, 3),
        (6, '과일말랑이', '말랑한 과일 모양 촉감 장난감이에요.', 150::BIGINT, 2),
        (7, '왁뿌볼', '재미있게 가지고 놀 수 있는 말랑한 공이에요.', 200::BIGINT, 1),
        (8, '배움공책 면제권', '배움공책 활동을 한 번 면제받을 수 있는 이용권이에요.', 90::BIGINT, 15)
)
INSERT INTO products (
    id,
    room_id,
    name,
    description,
    image_url,
    original_price,
    current_price,
    initial_stock,
    current_stock,
    status,
    display_order,
    updated_at
)
SELECT
    gen_random_uuid(),
    room.id,
    catalog.name,
    catalog.description,
    NULL,
    catalog.price,
    CASE
        WHEN room.inflation_applied_at IS NULL THEN catalog.price
        ELSE (
            catalog.price * (10_000 + rules.inflation_bps) + 5_000
        ) / 10_000
    END,
    catalog.stock,
    catalog.stock,
    'ON_SALE',
    catalog.display_order,
    CURRENT_TIMESTAMP
FROM rooms room
JOIN room_rules rules ON rules.room_id = room.id
CROSS JOIN catalog
ON CONFLICT (room_id, display_order) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    image_url = EXCLUDED.image_url,
    original_price = EXCLUDED.original_price,
    current_price = EXCLUDED.current_price,
    initial_stock = EXCLUDED.initial_stock,
    current_stock = EXCLUDED.current_stock,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
