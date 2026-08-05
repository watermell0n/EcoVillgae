-- Student character profile and paper-doll equipment.
-- inventory_items remains the ownership ledger. Equipment is reversible presentation state.
-- The character has one fixed body; only independently composable slots are persisted.

CREATE TABLE student_character_profiles (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    equipment_revision bigint NOT NULL DEFAULT 0,
    equipment_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_character_profiles_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_student_character_profiles_student
        UNIQUE (classroom_id, classroom_student_id),
    CONSTRAINT uq_student_character_profiles_owner
        UNIQUE (classroom_id, classroom_student_id, id),
    CONSTRAINT fk_student_character_profiles_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_character_profiles_version
        CHECK (state_version >= 0 AND equipment_revision >= 0)
);

INSERT INTO student_character_profiles (
    id,
    classroom_id,
    classroom_student_id,
    equipment_changed_at,
    created_at,
    updated_at
)
SELECT
    md5(student.id::text || ':student-character-profile')::uuid,
    student.classroom_id,
    student.id,
    student.created_at,
    student.created_at,
    student.updated_at
FROM classroom_students student;

ALTER TABLE inventory_items
    ADD CONSTRAINT uq_inventory_items_owner
    UNIQUE (classroom_id, classroom_student_id, id);

CREATE TABLE character_equipment (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    character_profile_id uuid NOT NULL,
    inventory_item_id uuid NOT NULL,
    slot varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    equipped_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_character_equipment_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_character_equipment_profile_slot
        UNIQUE (character_profile_id, slot),
    CONSTRAINT uq_character_equipment_inventory_item UNIQUE (inventory_item_id),
    CONSTRAINT fk_character_equipment_profile
        FOREIGN KEY (classroom_id, classroom_student_id, character_profile_id)
        REFERENCES student_character_profiles
            (classroom_id, classroom_student_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_character_equipment_owned_item
        FOREIGN KEY (classroom_id, classroom_student_id, inventory_item_id)
        REFERENCES inventory_items
            (classroom_id, classroom_student_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_character_equipment_slot
        CHECK (slot IN (
            'BACKGROUND', 'EFFECT', 'BACK', 'BOTTOM', 'SHOES',
            'TOP', 'NECK', 'FACE', 'HAND', 'HAT'
        )),
    CONSTRAINT ck_character_equipment_version CHECK (state_version >= 0)
);

-- Preserve legacy EQUIPPED rows. If old data contains more than one item for a slot, the most
-- recently equipped item wins and the remainder become unequipped ownership rows.
WITH ranked_equipment AS (
    SELECT
        item.*,
        CASE upper(split_part(catalog.character_code, '_', 1))
            WHEN 'BG' THEN 'BACKGROUND'
            ELSE upper(split_part(catalog.character_code, '_', 1))
        END AS slot,
        row_number() OVER (
            PARTITION BY item.classroom_id,
                         item.classroom_student_id,
                         CASE upper(split_part(catalog.character_code, '_', 1))
                             WHEN 'BG' THEN 'BACKGROUND'
                             ELSE upper(split_part(catalog.character_code, '_', 1))
                         END
            ORDER BY item.equipped_at DESC NULLS LAST, item.id
        ) AS slot_rank
    FROM inventory_items item
    JOIN character_catalog catalog
      ON catalog.id = item.character_catalog_id
    WHERE item.status = 'EQUIPPED'
)
INSERT INTO character_equipment (
    id,
    classroom_id,
    classroom_student_id,
    character_profile_id,
    inventory_item_id,
    slot,
    equipped_at,
    created_at,
    updated_at
)
SELECT
    md5(item.id::text || ':character-equipment')::uuid,
    item.classroom_id,
    item.classroom_student_id,
    profile.id,
    item.id,
    item.slot,
    coalesce(item.equipped_at, item.updated_at),
    coalesce(item.equipped_at, item.created_at),
    item.updated_at
FROM ranked_equipment item
JOIN student_character_profiles profile
  ON profile.classroom_id = item.classroom_id
 AND profile.classroom_student_id = item.classroom_student_id
WHERE item.slot_rank = 1
  AND item.slot IN (
      'BACKGROUND', 'EFFECT', 'BACK', 'BOTTOM', 'SHOES',
      'TOP', 'NECK', 'FACE', 'HAND', 'HAT'
  );

UPDATE student_character_profiles profile
   SET equipment_changed_at = greatest(
           profile.equipment_changed_at,
           migrated.latest_equipment_change
       ),
       updated_at = greatest(profile.updated_at, migrated.latest_equipment_change)
  FROM (
      SELECT classroom_id,
             classroom_student_id,
             max(greatest(equipped_at, updated_at)) AS latest_equipment_change
        FROM character_equipment
       GROUP BY classroom_id, classroom_student_id
  ) migrated
 WHERE profile.classroom_id = migrated.classroom_id
   AND profile.classroom_student_id = migrated.classroom_student_id;

-- Public projections continue to expose one representative equipped item while the profile
-- itself retains every slot for full paper-doll composition.
CREATE OR REPLACE VIEW public_quiz_leaderboard AS
SELECT
    result.classroom_id,
    result.quiz_session_id,
    student_result.rank,
    student.nickname,
    equipped.character_code,
    equipped.character_name
FROM quiz_result_versions result
JOIN quiz_sessions session
  ON session.classroom_id = result.classroom_id
 AND session.activity_session_id = result.quiz_session_id
JOIN quiz_student_results student_result
  ON student_result.classroom_id = result.classroom_id
 AND student_result.quiz_result_version_id = result.id
JOIN classroom_students student
  ON student.classroom_id = student_result.classroom_id
 AND student.id = student_result.classroom_student_id
LEFT JOIN LATERAL (
    SELECT
        catalog.character_code,
        catalog.name AS character_name
    FROM student_character_profiles profile
    JOIN character_equipment equipment
      ON equipment.classroom_id = profile.classroom_id
     AND equipment.classroom_student_id = profile.classroom_student_id
     AND equipment.character_profile_id = profile.id
    JOIN inventory_items item
      ON item.classroom_id = equipment.classroom_id
     AND item.classroom_student_id = equipment.classroom_student_id
     AND item.id = equipment.inventory_item_id
    JOIN character_catalog catalog
      ON catalog.id = item.character_catalog_id
    WHERE profile.classroom_id = student.classroom_id
      AND profile.classroom_student_id = student.id
    ORDER BY equipment.equipped_at DESC, equipment.id
    LIMIT 1
) equipped ON true
WHERE session.mode = 'GAME'
  AND session.leaderboard_public = true
  AND result.published_at IS NOT NULL
  AND student_result.rank BETWEEN 1 AND 3;

ALTER TABLE inventory_items DROP CONSTRAINT ck_inventory_items_lock_equip;
ALTER TABLE inventory_items DROP CONSTRAINT ck_inventory_items_status;

UPDATE inventory_items
   SET status = 'AVAILABLE',
       equipped_at = NULL,
       status_changed_at = greatest(status_changed_at, now()),
       updated_at = greatest(updated_at, now())
 WHERE status = 'EQUIPPED';

ALTER TABLE inventory_items DROP COLUMN equipped_at;
ALTER TABLE inventory_items
    ADD CONSTRAINT ck_inventory_items_status
        CHECK (status IN ('AVAILABLE', 'LOCKED_TRADE')),
    ADD CONSTRAINT ck_inventory_items_trade_lock
        CHECK ((status = 'LOCKED_TRADE') = (locked_trade_id IS NOT NULL));

CREATE INDEX ix_student_character_profiles_student
    ON student_character_profiles (classroom_id, classroom_student_id);
CREATE INDEX ix_character_equipment_profile
    ON character_equipment (classroom_id, character_profile_id);
CREATE INDEX ix_character_equipment_owner
    ON character_equipment (classroom_id, classroom_student_id);

CREATE FUNCTION validate_character_equipment_item()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    item_status varchar(40);
    item_slot varchar(40);
BEGIN
    SELECT item.status,
           CASE upper(split_part(catalog.character_code, '_', 1))
               WHEN 'BG' THEN 'BACKGROUND'
               ELSE upper(split_part(catalog.character_code, '_', 1))
           END
      INTO item_status, item_slot
      FROM inventory_items item
      JOIN character_catalog catalog ON catalog.id = item.character_catalog_id
     WHERE item.classroom_id = NEW.classroom_id
       AND item.classroom_student_id = NEW.classroom_student_id
       AND item.id = NEW.inventory_item_id
       FOR UPDATE OF item;

    IF NOT FOUND OR item_status <> 'AVAILABLE' THEN
        RAISE EXCEPTION 'only an available owned inventory item can be equipped';
    END IF;
    IF item_slot IS DISTINCT FROM NEW.slot THEN
        RAISE EXCEPTION 'equipment slot does not match character catalog code';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_character_equipment_validate_item
BEFORE INSERT OR UPDATE OF inventory_item_id, slot ON character_equipment
FOR EACH ROW EXECUTE FUNCTION validate_character_equipment_item();

CREATE FUNCTION prevent_equipped_inventory_lock()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status <> 'AVAILABLE'
       AND EXISTS (
           SELECT 1
             FROM character_equipment equipment
            WHERE equipment.classroom_id = NEW.classroom_id
              AND equipment.inventory_item_id = NEW.id
       ) THEN
        RAISE EXCEPTION 'equipped inventory item must be unequipped before trade lock';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_inventory_items_equipment_lock
BEFORE UPDATE OF status ON inventory_items
FOR EACH ROW EXECUTE FUNCTION prevent_equipped_inventory_lock();

-- Original code-rendered pixel catalog from the four approved product boards.
WITH catalog_seed(character_code, name) AS (
    VALUES
        ('hat_straw', '밀짚모자'),
        ('hat_beach', '비치 모자'),
        ('hat_pom_beanie', '방울 비니'),
        ('face_goggles', '물안경'),
        ('face_ski_goggles', '스키 고글'),
        ('neck_scarf', '목도리'),
        ('neck_knit_scarf', '니트 목도리'),
        ('top_swimsuit', '수영복'),
        ('top_padding', '패딩 점퍼'),
        ('shoes_red_boots', '빨간 장화'),
        ('shoes_sandals', '샌들'),
        ('shoes_aqua', '아쿠아슈즈'),
        ('shoes_snow_boots', '스노우 부츠'),
        ('effect_swim_tube', '튜브'),
        ('bg_spring', '봄 벚꽃'),
        ('bg_summer_sea', '여름 바다'),
        ('bg_autumn', '가을 단풍'),
        ('bg_winter', '겨울 눈'),
        ('hat_baseball_blue', '야구 모자 · 파랑'),
        ('hat_baseball_red', '야구 모자 · 빨강'),
        ('hat_baseball_navy', '야구 모자 · 남색'),
        ('hat_baseball_green', '야구 모자 · 초록'),
        ('hat_baseball_yellow', '야구 모자 · 노랑'),
        ('top_soccer', '축구 유니폼'),
        ('top_baseball_pinstripe', '핀스트라이프'),
        ('top_baseball_red', '야구복 · 빨강'),
        ('top_baseball_blue', '야구복 · 파랑'),
        ('top_baseball_navy', '야구복 · 남색'),
        ('top_baseball_gray', '원정 회색'),
        ('hand_baseball_glove', '야구 글러브'),
        ('hat_chef', '요리사 모자'),
        ('hat_safari', '탐험가 모자'),
        ('hat_space_helmet', '우주 헬멧'),
        ('hat_safety_police', '안전 경찰 모자'),
        ('hat_pilot', '파일럿 모자'),
        ('top_spacesuit', '우주복'),
        ('top_safety_police', '안전 경찰복'),
        ('top_legal_professional', '법조인 정장'),
        ('top_pilot', '파일럿 제복'),
        ('shoes_space_boots', '우주 부츠'),
        ('back_briefcase', '서류 가방'),
        ('bg_space', '우주 배경'),
        ('hat_crown', '왕관'),
        ('hat_wizard', '마법사 모자'),
        ('hat_knight', '기사 투구'),
        ('hat_dinosaur', '공룡 후드'),
        ('top_wizard_robe', '마법사 로브'),
        ('top_fantasy_armor', '판타지 갑옷'),
        ('top_dinosaur', '공룡 옷'),
        ('back_fairy_wings', '요정 날개'),
        ('back_dinosaur_tail', '공룡 꼬리'),
        ('hand_magic_wand', '별빛 마법봉'),
        ('hand_toy_sword', '장난감 검'),
        ('effect_fluffy_cloud', '몽실 구름')
)
INSERT INTO character_catalog (
    id,
    character_code,
    name,
    source_title,
    source_url,
    rights_status,
    rights_evidence_key,
    reviewed_at,
    status
)
SELECT
    md5('ecovillage-character:' || seed.character_code)::uuid,
    seed.character_code,
    seed.name,
    'EcoVillage code-rendered pixel catalog',
    'repo://student-app/src/features/character/pixelRenderer.ts',
    'ORIGINAL',
    'repo:code-pixel-catalog-v1',
    now(),
    'ACTIVE'
FROM catalog_seed seed
ON CONFLICT (character_code) DO NOTHING;

CREATE FUNCTION default_character_price(character_code varchar)
RETURNS bigint
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE character_code
        WHEN 'hat_crown' THEN 80
        WHEN 'bg_spring' THEN 80
        WHEN 'bg_summer_sea' THEN 80
        WHEN 'bg_autumn' THEN 80
        WHEN 'bg_winter' THEN 80
        WHEN 'bg_space' THEN 80
        WHEN 'top_spacesuit' THEN 60
        WHEN 'top_padding' THEN 55
        WHEN 'top_baseball_pinstripe' THEN 55
        WHEN 'top_legal_professional' THEN 55
        WHEN 'top_wizard_robe' THEN 55
        WHEN 'top_fantasy_armor' THEN 55
        WHEN 'top_dinosaur' THEN 55
        WHEN 'top_baseball_red' THEN 50
        WHEN 'top_baseball_blue' THEN 50
        WHEN 'top_baseball_navy' THEN 50
        WHEN 'top_baseball_gray' THEN 50
        WHEN 'top_safety_police' THEN 50
        WHEN 'top_pilot' THEN 55
        WHEN 'back_fairy_wings' THEN 50
        WHEN 'hat_space_helmet' THEN 45
        WHEN 'top_soccer' THEN 45
        WHEN 'effect_fluffy_cloud' THEN 45
        WHEN 'top_swimsuit' THEN 40
        WHEN 'shoes_snow_boots' THEN 40
        WHEN 'shoes_space_boots' THEN 40
        WHEN 'hat_wizard' THEN 40
        WHEN 'hat_knight' THEN 40
        WHEN 'hat_safari' THEN 35
        WHEN 'hat_pilot' THEN 35
        WHEN 'hat_dinosaur' THEN 35
        WHEN 'shoes_red_boots' THEN 35
        WHEN 'effect_swim_tube' THEN 35
        WHEN 'hat_pom_beanie' THEN 30
        WHEN 'hat_baseball_blue' THEN 30
        WHEN 'hat_baseball_red' THEN 30
        WHEN 'hat_baseball_navy' THEN 30
        WHEN 'hat_baseball_green' THEN 30
        WHEN 'hat_baseball_yellow' THEN 30
        WHEN 'hat_chef' THEN 30
        WHEN 'hat_safety_police' THEN 30
        WHEN 'back_briefcase' THEN 30
        WHEN 'back_dinosaur_tail' THEN 30
        WHEN 'hand_baseball_glove' THEN 30
        WHEN 'hand_magic_wand' THEN 30
        WHEN 'hand_toy_sword' THEN 30
        ELSE 25
    END;
$$;

CREATE FUNCTION seed_default_character_store(
    target_classroom_id uuid,
    teacher_user_id uuid
)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO classroom_characters (
        id,
        classroom_id,
        character_catalog_id,
        display_name,
        sort_order,
        status
    )
    SELECT
        md5(target_classroom_id::text || ':classroom-character:' || catalog.id::text)::uuid,
        target_classroom_id,
        catalog.id,
        catalog.name,
        row_number() OVER (ORDER BY catalog.character_code),
        'ACTIVE'
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
    ON CONFLICT (classroom_id, character_catalog_id) DO NOTHING;

    INSERT INTO store_products (
        id,
        classroom_id,
        name,
        description,
        product_kind,
        created_by_user_id,
        status
    )
    SELECT
        md5(target_classroom_id::text || ':store-product:' || catalog.character_code)::uuid,
        target_classroom_id,
        catalog.name,
        '코드형 도트 레이어 캐릭터 상품',
        'CHARACTER',
        teacher_user_id,
        'PUBLISHED'
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO store_product_versions (
        id,
        classroom_id,
        store_product_id,
        version,
        product_kind,
        unit_price,
        vat_rate_bps,
        purchasable,
        metadata,
        published_at
    )
    SELECT
        md5(target_classroom_id::text || ':store-product-version:' || catalog.character_code)::uuid,
        target_classroom_id,
        md5(target_classroom_id::text || ':store-product:' || catalog.character_code)::uuid,
        1,
        'CHARACTER',
        default_character_price(catalog.character_code),
        0,
        true,
        jsonb_build_object('character_catalog_id', catalog.id),
        now()
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
    ON CONFLICT (store_product_id, version) DO NOTHING;

    INSERT INTO store_stocks (
        store_product_id,
        classroom_id,
        available_quantity,
        reserved_quantity
    )
    SELECT
        md5(target_classroom_id::text || ':store-product:' || catalog.character_code)::uuid,
        target_classroom_id,
        99,
        0
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
    ON CONFLICT (store_product_id) DO NOTHING;

    UPDATE classroom_characters classroom_character
       SET store_product_id = md5(
               target_classroom_id::text
               || ':store-product:'
               || catalog.character_code
           )::uuid,
           updated_at = now()
      FROM character_catalog catalog
     WHERE classroom_character.classroom_id = target_classroom_id
       AND classroom_character.character_catalog_id = catalog.id
       AND classroom_character.store_product_id IS NULL
       AND catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1';
END;
$$;

CREATE FUNCTION seed_character_store_for_teacher()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.ended_at IS NULL THEN
        PERFORM seed_default_character_store(NEW.classroom_id, NEW.user_id);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_classroom_teacher_seed_character_store
AFTER INSERT OR UPDATE OF ended_at ON classroom_teachers
FOR EACH ROW EXECUTE FUNCTION seed_character_store_for_teacher();

DO $$
DECLARE
    teacher_record record;
BEGIN
    FOR teacher_record IN
        SELECT DISTINCT ON (teacher.classroom_id)
               teacher.classroom_id,
               teacher.user_id
          FROM classroom_teachers teacher
         WHERE teacher.ended_at IS NULL
         ORDER BY teacher.classroom_id,
                  CASE WHEN teacher.role = 'OWNER' THEN 0 ELSE 1 END,
                  teacher.assigned_at
    LOOP
        PERFORM seed_default_character_store(
            teacher_record.classroom_id,
            teacher_record.user_id
        );
    END LOOP;
END;
$$;
