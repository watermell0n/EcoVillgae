-- 멜빵바지(bottom_overalls) 판매 종료.
--
-- 프론트 카탈로그에서 이 상품을 뺐다. 서버가 계속 PUBLISHED로 내려주면 학생 앱이
-- 매칭 안 되는 상품을 조용히 버리게 되는데, 그게 이번에 고친 "10종만 보이던" 문제와
-- 같은 종류다. 서버에서도 학생 카탈로그에 나오지 않게 상태를 내린다.
--
-- 지우지 않고 상태만 내리는 이유:
--   * 이미 구매한 학생의 inventory_items·character_equipment 행은 남긴다. 하드 삭제하면
--     학생이 쓴 재화의 흔적이 사라지고, FK가 ON DELETE RESTRICT라 삭제도 막힌다
--   * store_product_versions·store_stocks도 남긴다. 과거 거래의 가격 근거다
--   * 되돌리려면 상태 세 줄을 원복하면 된다
--
-- 상태 값 선택:
--   store_products   -> 'UNPUBLISHED'.  StoreProductStatus 열거형이 DRAFT/PUBLISHED/
--                       UNPUBLISHED 셋뿐이라 'RETIRED'를 넣으면 JPA 매핑이 깨진다.
--                       학생 조회는 PUBLISHED만 읽으므로 이걸로 카탈로그에서 빠진다
--   character_catalog -> 'RETIRED'.  CharacterCatalog가 ACTIVE/APPROVED만 사용 가능으로 본다
--   classroom_characters -> 'RETIRED'.  컬럼이 varchar라 제약 없이 들어간다

-- 1) 원본 카탈로그를 종료 상태로 내린다.
UPDATE character_catalog
   SET status = 'RETIRED',
       updated_at = now()
 WHERE character_code = 'bottom_overalls';

-- 2) 교실별 캐릭터 행도 함께 내린다.
UPDATE classroom_characters classroom_character
   SET status = 'RETIRED',
       status_changed_at = now(),
       updated_at = now()
  FROM character_catalog catalog
 WHERE classroom_character.character_catalog_id = catalog.id
   AND catalog.character_code = 'bottom_overalls';

-- 3) 학생 상점에서 빠지도록 상품을 비공개로 내린다.
--    학생 조회 경로는 status = 'PUBLISHED'만 읽는다(StoreCatalogQueryService).
UPDATE store_products product
   SET status = 'UNPUBLISHED',
       updated_at = now()
  FROM classroom_characters classroom_character
  JOIN character_catalog catalog
    ON catalog.id = classroom_character.character_catalog_id
 WHERE product.id = classroom_character.store_product_id
   AND product.classroom_id = classroom_character.classroom_id
   AND catalog.character_code = 'bottom_overalls';

-- 4) 새로 만들어지는 교실에는 아예 넣지 않는다.
--    V1_22의 시드는 rights_evidence_key만 봤다. 종료된 카탈로그 행을 건너뛰도록
--    status 조건을 더한다. 그 밖의 동작은 V1_22와 같다.
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
      AND catalog.status IN ('ACTIVE', 'APPROVED')
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
      AND catalog.status IN ('ACTIVE', 'APPROVED')
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
      AND catalog.status IN ('ACTIVE', 'APPROVED')
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
      AND catalog.status IN ('ACTIVE', 'APPROVED')
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
       AND catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
       AND catalog.status IN ('ACTIVE', 'APPROVED');
END;
$$;

-- default_character_description / default_character_sort_order의 bottom_overalls 분기는
-- 남겨 둔다. 시드가 이 코드를 더 넘기지 않으므로 죽은 분기이고, 되돌릴 때 그대로 쓰인다.
