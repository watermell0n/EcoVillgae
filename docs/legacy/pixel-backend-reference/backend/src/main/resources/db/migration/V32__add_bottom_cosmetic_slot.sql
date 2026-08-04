-- 캐릭터 코스메틱 BOTTOM(하의) 슬롯 신설 + 시드 정합화.
-- 기획: docs/plans/2026-07-29-character-cosmetic-pricing-and-seed.md §3 (BOTTOM 5종),
--       docs/plans/2026-07-29-character-cosmetic-module-plan.md §6 (10슬롯).
-- V31 에서 FACE/NECK/BACK/HAND/EFFECT 분리와 theme 컬럼은 이미 완료되었으므로
-- 여기서는 BOTTOM 만 추가하고 단일 원본 카탈로그(character_cosmetic_seed_catalog)로 재동기화한다.

-- 1) slot CHECK 제약에 BOTTOM 추가 (레이어: 하의 = base 위, 신발/상의 아래).
ALTER TABLE character_cosmetics DROP CONSTRAINT IF EXISTS character_cosmetics_slot_check;
ALTER TABLE character_cosmetics ADD CONSTRAINT character_cosmetics_slot_check CHECK (
    slot IN (
        'HAT', 'FACE', 'NECK', 'TOP', 'BOTTOM', 'SHOES',
        'BACK', 'HAND', 'EFFECT', 'BACKGROUND'
    )
);

-- 2) anchor 를 시드 카탈로그의 컬럼으로 승격한다.
--    (기존에는 슬롯별 CASE 를 마이그레이션·데모시드·서비스에서 각각 중복 표기했다.)
--    이렇게 하면 새 방 시드가 catalog.anchor 를 그대로 복사해 단일 원본을 유지한다.
ALTER TABLE character_cosmetic_seed_catalog ADD COLUMN IF NOT EXISTS anchor JSONB;

-- 3) 단일 원본 시드 카탈로그에 BOTTOM 5종 추가 (순번은 기존 72 뒤에 이어붙인다).
--    테마는 pricing-and-seed 문서에서 BOTTOM 미지정이라 필터/컬렉션 용도로 상식적으로 배정한다.
INSERT INTO character_cosmetic_seed_catalog (
    display_order, slot, theme, name, sprite_key, price, anchor
) VALUES
    (73, 'BOTTOM', 'BASIC',    '줄무늬 바지', 'bottom_striped_pants', 40::BIGINT, '{"x":0.5,"y":0.76}'::jsonb),
    (74, 'BOTTOM', 'BASIC',    '청바지',      'bottom_jeans',         40::BIGINT, '{"x":0.5,"y":0.76}'::jsonb),
    (75, 'BOTTOM', 'SEASONAL', '반바지',      'bottom_shorts',        30::BIGINT, '{"x":0.5,"y":0.76}'::jsonb),
    (76, 'BOTTOM', 'BASIC',    '치마',        'bottom_skirt',         35::BIGINT, '{"x":0.5,"y":0.76}'::jsonb),
    (77, 'BOTTOM', 'EVENT',    '정장 바지',   'bottom_slacks',        45::BIGINT, '{"x":0.5,"y":0.76}'::jsonb)
ON CONFLICT (display_order) DO NOTHING;

-- 4) 기존 72행(V31 시드)의 anchor 를 슬롯별 기준값으로 백필한다.
UPDATE character_cosmetic_seed_catalog SET anchor = CASE slot
        WHEN 'HAT'        THEN '{"x":0.5,"y":0.05}'::jsonb
        WHEN 'FACE'       THEN '{"x":0.5,"y":0.30}'::jsonb
        WHEN 'NECK'       THEN '{"x":0.5,"y":0.40}'::jsonb
        WHEN 'TOP'        THEN '{"x":0.5,"y":0.60}'::jsonb
        WHEN 'BOTTOM'     THEN '{"x":0.5,"y":0.76}'::jsonb
        WHEN 'SHOES'      THEN '{"x":0.5,"y":0.92}'::jsonb
        WHEN 'BACK'       THEN '{"x":0.5,"y":0.58}'::jsonb
        WHEN 'HAND'       THEN '{"x":0.78,"y":0.65}'::jsonb
        WHEN 'EFFECT'     THEN '{"x":0.5,"y":0.78}'::jsonb
        WHEN 'BACKGROUND' THEN '{"x":0.5,"y":0.50}'::jsonb
    END
WHERE anchor IS NULL;

ALTER TABLE character_cosmetic_seed_catalog ALTER COLUMN anchor SET NOT NULL;

-- 5) 카탈로그(이제 77종)를 모든 기존 방에 재동기화한다.
--    기존 72행은 sprite_key 로 매칭돼 동일 값으로 갱신되고 BOTTOM 5행이 신규 삽입된다.
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
    catalog.anchor,
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
