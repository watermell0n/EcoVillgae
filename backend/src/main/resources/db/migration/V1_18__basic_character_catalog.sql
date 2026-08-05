-- Add the ten basic-theme paper-doll parts from the approved reference board.
-- Every item remains a code-rendered layer; no raster asset is stored.

WITH catalog_seed(character_code, name) AS (
    VALUES
        ('hat_red_cap', '빨간 캡'),
        ('hat_teal_beanie', '비니'),
        ('face_sunglasses', '선글라스'),
        ('face_headphones', '헤드폰'),
        ('top_blue_tshirt', '파란 티셔츠'),
        ('top_yellow_hoodie', '노란 후드'),
        ('top_green_vest', '초록 조끼'),
        ('bottom_overalls', '멜빵바지'),
        ('shoes_sneakers', '운동화'),
        ('back_backpack', '배낭')
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

CREATE OR REPLACE FUNCTION default_character_price(character_code varchar)
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
        WHEN 'top_pilot' THEN 55
        WHEN 'top_baseball_red' THEN 50
        WHEN 'top_baseball_blue' THEN 50
        WHEN 'top_baseball_navy' THEN 50
        WHEN 'top_baseball_gray' THEN 50
        WHEN 'top_safety_police' THEN 50
        WHEN 'back_fairy_wings' THEN 50
        WHEN 'hat_space_helmet' THEN 45
        WHEN 'top_soccer' THEN 45
        WHEN 'effect_fluffy_cloud' THEN 45
        WHEN 'top_yellow_hoodie' THEN 45
        WHEN 'bottom_overalls' THEN 45
        WHEN 'top_swimsuit' THEN 40
        WHEN 'shoes_snow_boots' THEN 40
        WHEN 'shoes_space_boots' THEN 40
        WHEN 'hat_wizard' THEN 40
        WHEN 'hat_knight' THEN 40
        WHEN 'top_green_vest' THEN 40
        WHEN 'back_backpack' THEN 40
        WHEN 'hat_safari' THEN 35
        WHEN 'hat_pilot' THEN 35
        WHEN 'hat_dinosaur' THEN 35
        WHEN 'shoes_red_boots' THEN 35
        WHEN 'effect_swim_tube' THEN 35
        WHEN 'top_blue_tshirt' THEN 35
        WHEN 'face_headphones' THEN 35
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
        WHEN 'hat_red_cap' THEN 30
        WHEN 'hat_teal_beanie' THEN 30
        WHEN 'shoes_sneakers' THEN 30
        ELSE 25
    END;
$$;

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
