ALTER TABLE products
    DROP CONSTRAINT IF EXISTS products_status_check;

ALTER TABLE products
    ADD CONSTRAINT products_status_check
        CHECK (status IN ('DRAFT', 'ON_SALE', 'PAUSED', 'SOLD_OUT', 'DISCONTINUED'));
