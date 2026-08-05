-- 캐릭터 상점 상품 설명과 노출 순서.
--
-- 상점 상세 카드에 상품 설명이 필요한데 지금은 64종이 모두 '코드형 도트 레이어 캐릭터 상품'
-- 하나를 공유한다. 프론트에 공통 문구를 하드코딩하지 않고 백엔드가 상품별 문구를 준다.
--
-- 설명 원칙: 초등학생이 읽을 수 있는 쉬운 표현, 한두 문장, 색·분위기·착용감 중심.
-- 성능이나 희소성 표현은 쓰지 않는다.
--
-- sort_order는 상점 '추천' 탭의 기본 노출 순서다. 기본 코디 한 벌(모자·상의·하의·신발·
-- 배경·등장식)이 첫 화면에 오도록 앞쪽에 두고, 이후 계절 → 스포츠 → 직업 → 판타지로 묶는다.
--
-- 가격·VAT·화폐 단위는 이 마이그레이션에서 바꾸지 않는다. 별도 마이그레이션으로 처리한다.

CREATE FUNCTION default_character_description(character_code varchar)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE character_code
        -- 기본
        WHEN 'hat_red_cap' THEN '활기찬 빨간색이 돋보이는 스포티한 모자예요.'
        WHEN 'hat_teal_beanie' THEN '푹 눌러쓰는 청록색 비니예요. 머리가 폭 감싸여 따뜻해 보여요.'
        WHEN 'face_sunglasses' THEN '까만 렌즈가 반짝이는 선글라스예요. 쓰면 조금 멋있어 보여요.'
        WHEN 'face_headphones' THEN '귀를 감싸는 회색 헤드폰이에요. 음악을 듣는 것 같은 모습이 돼요.'
        WHEN 'top_blue_tshirt' THEN '시원한 파란색 티셔츠예요. 어떤 하의와도 잘 어울려요.'
        WHEN 'top_yellow_hoodie' THEN '포근한 느낌의 노란색 후드예요. 편안하고 밝은 분위기를 만들어 줘요.'
        WHEN 'top_green_vest' THEN '주머니가 달린 초록 조끼예요. 활동적인 느낌을 줘요.'
        WHEN 'bottom_overalls' THEN '멜빵이 달린 청색 바지예요. 씩씩한 느낌이 들어요.'
        WHEN 'shoes_sneakers' THEN '흰 바탕에 빨간 줄이 들어간 운동화예요. 달리고 싶어지는 신발이에요.'
        WHEN 'back_backpack' THEN '어깨끈이 보이는 갈색 배낭이에요. 소풍 가는 기분이 들어요.'

        -- 계절
        WHEN 'hat_straw' THEN '넓은 챙이 있는 밀짚모자예요. 햇볕이 쨍쨍한 날에 잘 어울려요.'
        WHEN 'hat_beach' THEN '파란 리본을 두른 크림색 비치 모자예요. 바닷가 분위기가 나요.'
        WHEN 'hat_pom_beanie' THEN '방울이 달린 초록 비니예요. 겨울에 쓰면 아주 따뜻해 보여요.'
        WHEN 'face_goggles' THEN '눈을 덮는 파란 물안경이에요. 물놀이 준비가 끝난 모습이 돼요.'
        WHEN 'face_ski_goggles' THEN '주황 렌즈가 넓은 스키 고글이에요. 눈밭을 달릴 것 같아요.'
        WHEN 'neck_scarf' THEN '목에 두르는 빨간 목도리예요. 끝이 살짝 내려와 귀여워요.'
        WHEN 'neck_knit_scarf' THEN '두툼하게 짠 초록 목도리예요. 목이 따뜻해 보여요.'
        WHEN 'top_swimsuit' THEN '어깨끈이 있는 파란 수영복이에요. 물놀이할 때 딱이에요.'
        WHEN 'top_padding' THEN '빵빵한 빨간 패딩 점퍼예요. 아주 추운 날에도 든든해 보여요.'
        WHEN 'shoes_red_boots' THEN '발목까지 올라오는 빨간 장화예요. 비 오는 날이 기다려져요.'
        WHEN 'shoes_sandals' THEN '발등에 끈이 있는 갈색 샌들이에요. 시원한 느낌이 들어요.'
        WHEN 'shoes_aqua' THEN '물이 잘 빠지는 하늘색 아쿠아슈즈예요. 물놀이할 때 편해요.'
        WHEN 'shoes_snow_boots' THEN '털이 달린 하얀 스노우 부츠예요. 눈길에서도 따뜻해 보여요.'
        WHEN 'effect_swim_tube' THEN '허리에 끼우는 주황색 튜브예요. 물에 뜬 것 같은 모습이 돼요.'
        WHEN 'bg_spring' THEN '분홍 벚꽃이 활짝 핀 봄 풍경이에요. 배경이 환해져요.'
        WHEN 'bg_summer_sea' THEN '파란 바다와 모래사장이 보이는 여름 풍경이에요.'
        WHEN 'bg_autumn' THEN '노랗고 빨갛게 물든 가을 나무가 있는 풍경이에요.'
        WHEN 'bg_winter' THEN '눈이 내린 겨울 숲 풍경이에요. 조용하고 시원해 보여요.'

        -- 스포츠
        WHEN 'hat_baseball_blue' THEN '앞에 흰 마크가 있는 파란 야구 모자예요.'
        WHEN 'hat_baseball_red' THEN '앞에 흰 마크가 있는 빨간 야구 모자예요.'
        WHEN 'hat_baseball_navy' THEN '앞에 흰 마크가 있는 남색 야구 모자예요.'
        WHEN 'hat_baseball_green' THEN '앞에 흰 마크가 있는 초록 야구 모자예요.'
        WHEN 'hat_baseball_yellow' THEN '앞에 흰 마크가 있는 노란 야구 모자예요.'
        WHEN 'top_soccer' THEN '등번호가 들어간 주황 축구 유니폼이에요. 운동장이 생각나요.'
        WHEN 'top_baseball_pinstripe' THEN '세로 줄무늬가 들어간 흰 야구복이에요. 단정해 보여요.'
        WHEN 'top_baseball_red' THEN '단추가 줄지어 달린 빨간 야구복이에요.'
        WHEN 'top_baseball_blue' THEN '단추가 줄지어 달린 파란 야구복이에요.'
        WHEN 'top_baseball_navy' THEN '단추가 줄지어 달린 남색 야구복이에요.'
        WHEN 'top_baseball_gray' THEN '원정 경기에서 입는 회색 야구복이에요.'
        WHEN 'hand_baseball_glove' THEN '손에 끼우는 갈색 야구 글러브예요. 공을 받을 준비가 돼요.'

        -- 직업
        WHEN 'hat_chef' THEN '높고 하얀 요리사 모자예요. 맛있는 요리를 만들 것 같아요.'
        WHEN 'hat_safari' THEN '챙이 넓은 탐험가 모자예요. 어디든 떠날 수 있을 것 같아요.'
        WHEN 'hat_space_helmet' THEN '얼굴이 보이는 둥근 우주 헬멧이에요. 우주로 나갈 준비가 돼요.'
        WHEN 'hat_safety_police' THEN '금색 띠를 두른 남색 안전 경찰 모자예요.'
        WHEN 'hat_pilot' THEN '금장이 달린 흰 파일럿 모자예요. 하늘을 날 것 같아요.'
        WHEN 'top_spacesuit' THEN '가슴에 조작판이 있는 흰 우주복이에요. 온몸을 감싸 줘요.'
        WHEN 'top_safety_police' THEN '배지가 달린 남색 안전 경찰복이에요. 씩씩해 보여요.'
        WHEN 'top_legal_professional' THEN '넥타이를 맨 검정 정장이에요. 아주 단정해 보여요.'
        WHEN 'top_pilot' THEN '어깨에 금장이 있는 흰 파일럿 제복이에요.'
        WHEN 'shoes_space_boots' THEN '두툼한 흰 우주 부츠예요. 발이 든든해 보여요.'
        WHEN 'back_briefcase' THEN '손에 드는 갈색 서류 가방이에요. 어른스러운 느낌이 나요.'
        WHEN 'bg_space' THEN '별이 반짝이는 우주 풍경이에요. 밤처럼 어둡고 조용해요.'

        -- 판타지
        WHEN 'hat_crown' THEN '보석이 박힌 금색 왕관이에요. 머리에 반짝임을 더해 줘요.'
        WHEN 'hat_wizard' THEN '별이 달린 뾰족한 보라색 마법사 모자예요.'
        WHEN 'hat_knight' THEN '얼굴을 감싸는 은색 기사 투구예요. 아주 단단해 보여요.'
        WHEN 'hat_dinosaur' THEN '등에 지느러미가 달린 초록 공룡 후드예요. 얼굴은 그대로 보여요.'
        WHEN 'top_wizard_robe' THEN '금색 장식이 있는 보라색 마법사 로브예요. 발까지 길게 내려와요.'
        WHEN 'top_fantasy_armor' THEN '가슴판이 달린 은색 갑옷이에요. 모험을 떠날 것 같아요.'
        WHEN 'top_dinosaur' THEN '배가 연두색인 초록 공룡 옷이에요. 온몸을 감싸 줘요.'
        WHEN 'back_fairy_wings' THEN '몸 뒤로 활짝 펴지는 하늘색 요정 날개예요.'
        WHEN 'back_dinosaur_tail' THEN '옆으로 길게 뻗은 초록 공룡 꼬리예요.'
        WHEN 'hand_magic_wand' THEN '끝에 금색 별이 달린 마법봉이에요. 주문을 외울 것 같아요.'
        WHEN 'hand_toy_sword' THEN '손잡이가 금색인 장난감 검이에요. 씩씩하게 들 수 있어요.'
        WHEN 'effect_fluffy_cloud' THEN '발밑에 깔리는 몽실몽실한 구름이에요. 구름 위에 선 것 같아요.'

        ELSE '내 캐릭터를 꾸며 주는 도트 아이템이에요.'
    END;
$$;

CREATE FUNCTION default_character_sort_order(character_code varchar)
RETURNS integer
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE character_code
        -- 기본 코디 한 벌이 '추천' 첫 화면에 오도록 앞쪽에 둔다.
        WHEN 'hat_red_cap' THEN 10
        WHEN 'top_blue_tshirt' THEN 20
        WHEN 'bottom_overalls' THEN 30
        WHEN 'shoes_sneakers' THEN 40
        WHEN 'bg_spring' THEN 50
        WHEN 'back_backpack' THEN 60
        WHEN 'top_yellow_hoodie' THEN 70
        WHEN 'hat_teal_beanie' THEN 80
        WHEN 'face_sunglasses' THEN 90
        WHEN 'top_green_vest' THEN 100
        WHEN 'face_headphones' THEN 110

        -- 계절
        WHEN 'hat_straw' THEN 120
        WHEN 'hat_beach' THEN 130
        WHEN 'hat_pom_beanie' THEN 140
        WHEN 'face_goggles' THEN 150
        WHEN 'face_ski_goggles' THEN 160
        WHEN 'neck_scarf' THEN 170
        WHEN 'neck_knit_scarf' THEN 180
        WHEN 'top_swimsuit' THEN 190
        WHEN 'top_padding' THEN 200
        WHEN 'shoes_red_boots' THEN 210
        WHEN 'shoes_sandals' THEN 220
        WHEN 'shoes_aqua' THEN 230
        WHEN 'shoes_snow_boots' THEN 240
        WHEN 'effect_swim_tube' THEN 250
        WHEN 'bg_summer_sea' THEN 260
        WHEN 'bg_autumn' THEN 270
        WHEN 'bg_winter' THEN 280

        -- 스포츠
        WHEN 'hat_baseball_blue' THEN 290
        WHEN 'hat_baseball_red' THEN 300
        WHEN 'hat_baseball_navy' THEN 310
        WHEN 'hat_baseball_green' THEN 320
        WHEN 'hat_baseball_yellow' THEN 330
        WHEN 'top_soccer' THEN 340
        WHEN 'top_baseball_pinstripe' THEN 350
        WHEN 'top_baseball_red' THEN 360
        WHEN 'top_baseball_blue' THEN 370
        WHEN 'top_baseball_navy' THEN 380
        WHEN 'top_baseball_gray' THEN 390
        WHEN 'hand_baseball_glove' THEN 400

        -- 직업
        WHEN 'hat_chef' THEN 410
        WHEN 'hat_safari' THEN 420
        WHEN 'hat_space_helmet' THEN 430
        WHEN 'hat_safety_police' THEN 440
        WHEN 'hat_pilot' THEN 450
        WHEN 'top_spacesuit' THEN 460
        WHEN 'top_safety_police' THEN 470
        WHEN 'top_legal_professional' THEN 480
        WHEN 'top_pilot' THEN 490
        WHEN 'shoes_space_boots' THEN 500
        WHEN 'back_briefcase' THEN 510
        WHEN 'bg_space' THEN 520

        -- 판타지
        WHEN 'hat_crown' THEN 530
        WHEN 'hat_wizard' THEN 540
        WHEN 'hat_knight' THEN 550
        WHEN 'hat_dinosaur' THEN 560
        WHEN 'top_wizard_robe' THEN 570
        WHEN 'top_fantasy_armor' THEN 580
        WHEN 'top_dinosaur' THEN 590
        WHEN 'back_fairy_wings' THEN 600
        WHEN 'back_dinosaur_tail' THEN 610
        WHEN 'hand_magic_wand' THEN 620
        WHEN 'hand_toy_sword' THEN 630
        WHEN 'effect_fluffy_cloud' THEN 640

        -- 새 상품이 들어와도 목록 끝에 붙기만 하고 기존 순서를 흔들지 않는다.
        ELSE 900
    END;
$$;

-- 이미 만들어진 교실의 상품 설명을 채운다. 캐릭터 상품만 대상이며 이름·가격은 손대지 않는다.
UPDATE store_products product
   SET description = default_character_description(catalog.character_code),
       updated_at = now()
  FROM classroom_characters classroom_character
  JOIN character_catalog catalog
    ON catalog.id = classroom_character.character_catalog_id
 WHERE classroom_character.classroom_id = product.classroom_id
   AND classroom_character.store_product_id = product.id
   AND product.product_kind = 'CHARACTER'
   AND default_character_description(catalog.character_code)
       IS DISTINCT FROM product.description;

-- 노출 순서도 함께 맞춘다. 기존 값은 character_code 알파벳순이라 상점에서 의미가 없었다.
UPDATE classroom_characters classroom_character
   SET sort_order = default_character_sort_order(catalog.character_code),
       updated_at = now()
  FROM character_catalog catalog
 WHERE catalog.id = classroom_character.character_catalog_id
   AND default_character_sort_order(catalog.character_code)
       IS DISTINCT FROM classroom_character.sort_order;

-- 새로 만들어지는 교실도 같은 설명·순서를 갖도록 시드 함수를 갱신한다.
CREATE OR REPLACE FUNCTION seed_default_character_store(
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
        default_character_sort_order(catalog.character_code),
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
        default_character_description(catalog.character_code),
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
