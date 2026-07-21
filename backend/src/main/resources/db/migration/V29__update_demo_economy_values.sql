ALTER TABLE room_rules
    ALTER COLUMN day2_correct_reward SET DEFAULT 10;

UPDATE room_rules
SET day2_correct_reward = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE day2_correct_reward <> 10;

WITH desired_prices(display_order, price) AS (
    VALUES
        (1, 45::BIGINT),
        (2, 80::BIGINT),
        (3, 40::BIGINT),
        (4, 100::BIGINT),
        (5, 145::BIGINT),
        (6, 180::BIGINT),
        (7, 250::BIGINT),
        (8, 110::BIGINT)
)
UPDATE products product
SET original_price = desired.price,
    current_price = CASE
        WHEN product.current_price = product.original_price THEN desired.price
        ELSE (desired.price * (10000 + rules.inflation_bps) + 9999) / 10000
    END,
    updated_at = CURRENT_TIMESTAMP
FROM desired_prices desired, room_rules rules
WHERE product.display_order = desired.display_order
  AND rules.room_id = product.room_id;

WITH desired_images(display_order, image_url) AS (
    VALUES
        (1, '/api/v1/product-images/b6fbfc5e-5580-42f3-82da-45caf5bc2b27.png'),
        (2, '/api/v1/product-images/61466d81-4880-4f87-9113-4d4cf27b7547.png'),
        (3, '/api/v1/product-images/4e97e1d8-008f-4c63-95e9-1b8553349120.png'),
        (4, '/api/v1/product-images/74ff9a68-1a71-4cfe-b1f4-71c64f54aa17.png'),
        (5, '/api/v1/product-images/73aa1e91-9296-4035-b5c0-4850a4c45c8a.png'),
        (6, '/api/v1/product-images/3c617161-dea7-4530-ae31-76d15dd3b7b9.png'),
        (7, '/api/v1/product-images/03a642cb-f92e-45ee-b0ac-a02cc303327d.png'),
        (8, '/api/v1/product-images/2a6df38e-e054-41c7-9716-15e0e321ff2d.png')
)
UPDATE products product
SET image_url = desired.image_url,
    updated_at = CURRENT_TIMESTAMP
FROM desired_images desired
WHERE product.display_order = desired.display_order;

UPDATE investment_scenario_returns scenario_return
SET return_bps = CASE asset.code
    WHEN 'SAFE_BOND' THEN 1500
    WHEN 'LOCAL_STORE' THEN -2000
    WHEN 'FUTURE_TECH' THEN 2000
END
FROM investment_assets asset
WHERE scenario_return.asset_id = asset.id
  AND asset.code IN ('SAFE_BOND', 'LOCAL_STORE', 'FUTURE_TECH');
