-- 캐릭터 상점 판매 범위를 기본 9종 + 배경 5종으로 좁힌다.
--
-- 코드로 생성한 49종은 판매하지 않는다. 지우지 않고 판매만 중단한다 —
-- 카탈로그 정의와 에셋은 그대로 두어야 이미 구매한 학생의 캐릭터가 계속 렌더된다.
--
-- 판매 유지 (14종)
--   모자   hat_red_cap, hat_teal_beanie
--   얼굴   face_sunglasses, face_headphones
--   상의   top_blue_tshirt, top_yellow_hoodie, top_green_vest
--   신발   shoes_sneakers
--   등     back_backpack
--   배경   bg_spring, bg_summer_sea, bg_autumn, bg_winter, bg_space
--
-- 하의·목도리·손·효과 슬롯은 판매 상품이 0종이 된다. 슬롯 키는 남는다 —
-- 전신 의상의 coversSlots가 BOTTOM을 참조하고, 이미 보유한 학생이 착용 중일 수 있다.
--
-- ## 상태를 네 군데 내리는 이유
--
-- 학생 노출과 구매를 막는 지점이 서로 다르다.
--
--   store_products.status = 'UNPUBLISHED'
--     학생 카탈로그 조회가 PUBLISHED만 읽는다(StoreCatalogQueryService). 목록에서 빠진다.
--     'RETIRED'는 쓸 수 없다 — StoreProductStatus 열거형이 DRAFT/PUBLISHED/UNPUBLISHED
--     셋뿐이라 JPA 매핑이 깨진다.
--
--   store_product_versions.purchasable = false
--     구매 경로(StorePurchaseService)는 product.status를 보지 않고 버전의
--     isPurchasableAt만 본다. 이걸 내리지 않으면 상품 id를 아는 클라이언트가
--     목록에 없는 상품을 계속 살 수 있다. V1_23이 멜빵바지에 남긴 구멍이라
--     여기서 함께 막는다.
--
--   character_catalog.status / classroom_characters.status = 'RETIRED'
--     V1_23이 시드에 status 조건을 넣었으므로 새로 만들어지는 교실은 이 49종을
--     아예 받지 않는다. isApproved()는 교사가 상품을 붙일 때만 검사하므로
--     학생의 착용·보유에는 영향이 없다.
--
-- 되돌리려면 네 UPDATE의 값을 원복한다. 구매 이력(store_orders·inventory_items)과
-- 가격 근거(store_product_versions 행)는 건드리지 않는다.

CREATE TEMPORARY TABLE off_sale_character_codes (character_code varchar(80) PRIMARY KEY);

INSERT INTO off_sale_character_codes (character_code)
VALUES
    ('hat_straw'), ('hat_beach'), ('hat_pom_beanie'),
    ('hat_baseball_blue'), ('hat_baseball_red'), ('hat_baseball_navy'),
    ('hat_baseball_green'), ('hat_baseball_yellow'),
    ('hat_chef'), ('hat_safari'), ('hat_space_helmet'), ('hat_safety_police'),
    ('hat_pilot'), ('hat_crown'), ('hat_wizard'), ('hat_knight'), ('hat_dinosaur'),
    ('face_goggles'), ('face_ski_goggles'),
    ('neck_scarf'), ('neck_knit_scarf'),
    ('top_swimsuit'), ('top_padding'), ('top_soccer'),
    ('top_baseball_pinstripe'), ('top_baseball_red'), ('top_baseball_blue'),
    ('top_baseball_navy'), ('top_baseball_gray'),
    ('top_spacesuit'), ('top_safety_police'), ('top_legal_professional'),
    ('top_pilot'), ('top_wizard_robe'), ('top_fantasy_armor'), ('top_dinosaur'),
    ('shoes_red_boots'), ('shoes_sandals'), ('shoes_aqua'),
    ('shoes_snow_boots'), ('shoes_space_boots'),
    ('back_briefcase'), ('back_fairy_wings'), ('back_dinosaur_tail'),
    ('hand_baseball_glove'), ('hand_magic_wand'), ('hand_toy_sword'),
    ('effect_swim_tube'), ('effect_fluffy_cloud');

-- 1) 원본 카탈로그를 종료 상태로 내린다. V1_23이 넣은 시드 조건이 이걸 보고 건너뛴다.
UPDATE character_catalog catalog
   SET status = 'RETIRED',
       updated_at = now()
  FROM off_sale_character_codes off_sale
 WHERE catalog.character_code = off_sale.character_code;

-- 2) 교실별 캐릭터 행도 함께 내린다.
UPDATE classroom_characters classroom_character
   SET status = 'RETIRED',
       status_changed_at = now(),
       updated_at = now()
  FROM character_catalog catalog
  JOIN off_sale_character_codes off_sale
    ON off_sale.character_code = catalog.character_code
 WHERE classroom_character.character_catalog_id = catalog.id;

-- 3) 학생 상점 목록에서 빠지도록 상품을 비공개로 내린다.
UPDATE store_products product
   SET status = 'UNPUBLISHED',
       updated_at = now()
  FROM classroom_characters classroom_character
  JOIN character_catalog catalog
    ON catalog.id = classroom_character.character_catalog_id
  JOIN off_sale_character_codes off_sale
    ON off_sale.character_code = catalog.character_code
 WHERE product.id = classroom_character.store_product_id
   AND product.classroom_id = classroom_character.classroom_id;

-- 4) 구매 자체를 막는다. 목록에 없어도 상품 id로는 살 수 있기 때문이다.
--    멜빵바지도 함께 막는다 — V1_23은 status만 내렸고 이 경로는 열려 있었다.
UPDATE store_product_versions product_version
   SET purchasable = false
  FROM classroom_characters classroom_character
  JOIN character_catalog catalog
    ON catalog.id = classroom_character.character_catalog_id
 WHERE product_version.store_product_id = classroom_character.store_product_id
   AND product_version.classroom_id = classroom_character.classroom_id
   AND (
        catalog.character_code = 'bottom_overalls'
        OR catalog.character_code IN (SELECT character_code FROM off_sale_character_codes)
   );

DROP TABLE off_sale_character_codes;
