-- 판매 종료한 캐릭터 상품의 "구매 경로"를 실제로 막는다.
--
-- ## V1_26이 막지 못한 이유
--
-- V1_26은 `store_product_versions.purchasable = false`로 구매를 막으려 했다.
-- 그런데 이 표에는 V1_8의 트리거가 걸려 있다.
--
--   trg_store_product_versions_published
--     → reject_mutation_when_published('published_at')
--
-- 이 함수는 `published_at`이 채워진 행에 대해 `updated_at`, `retired_at`,
-- `superseded_at`, `status` **네 열만** 바꾸도록 허용하고, 그 밖의 열이 달라지면
-- `store_product_versions published business fields are immutable` 예외를 던진다.
-- `purchasable`은 허용 목록에 없다.
--
-- 즉 게시된 캐릭터 상품이 한 건이라도 있는 데이터베이스에서는 V1_26의 4)번
-- UPDATE가 실패해 마이그레이션 자체가 멈춘다. 지금까지 문제가 드러나지 않은 것은
-- 해당 행이 0건이어서 트리거가 아예 실행되지 않았기 때문이다.
--
-- V1_26은 이미 적용된 파일이므로 고치지 않는다. 파일을 바꾸면 Flyway 체크섬이
-- 어긋나 이미 적용한 데이터베이스에서 애플리케이션이 시작되지 않는다.
-- 대신 트리거가 허용하는 `retired_at`으로 같은 목적을 여기서 다시 달성한다.
--
-- ## retired_at으로 막히는 근거
--
-- 구매 경로(StorePurchaseService)는 상품 상태가 아니라 버전의 isPurchasableAt만 본다.
--
--   purchasable AND published_at <= now AND (retired_at IS NULL OR now < retired_at)
--
-- `retired_at = now()`를 넣으면 그 시점부터 이 조건이 거짓이 되어 상품 id를 아는
-- 클라이언트도 더 이상 구매할 수 없다.
--
-- ## 함께 알아 둘 점
--
-- 버전을 retire하면 교사 상점 목록 조회(StoreCatalogQueryService)도 이 상품을
-- 더 이상 보여 주지 않는다. 최신 버전을 `retired_at IS NULL`로 찾기 때문이다.
-- 판매하지 않기로 한 49종 + 멜빵바지에는 의도한 결과다. 이미 구매한 학생의
-- 보유 아이템(inventory_items)과 구매 이력(store_orders)은 건드리지 않으므로
-- 캐릭터는 그대로 렌더된다.
--
-- 되돌리려면 이 UPDATE가 채운 `retired_at`을 NULL로 비운다.

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
    ('effect_swim_tube'), ('effect_fluffy_cloud'),
    -- 멜빵바지는 V1_25가 status만 내렸고 구매 경로는 열려 있었다. 여기서 함께 막는다.
    ('bottom_overalls');

-- 이미 retire된 버전은 건드리지 않는다. 다시 실행해도 결과가 같아야 하고,
-- 먼저 은퇴한 시점을 나중 시점으로 덮어써서 이력을 흐리지 않는다.
UPDATE store_product_versions product_version
   SET retired_at = now()
  FROM classroom_characters classroom_character
  JOIN character_catalog catalog
    ON catalog.id = classroom_character.character_catalog_id
  JOIN off_sale_character_codes off_sale
    ON off_sale.character_code = catalog.character_code
 WHERE product_version.store_product_id = classroom_character.store_product_id
   AND product_version.classroom_id = classroom_character.classroom_id
   AND product_version.retired_at IS NULL;

DROP TABLE off_sale_character_codes;
