CREATE TABLE products (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    image_url VARCHAR(500),
    original_price BIGINT NOT NULL CHECK (original_price > 0),
    current_price BIGINT NOT NULL CHECK (current_price > 0),
    initial_stock INTEGER NOT NULL CHECK (initial_stock >= 0),
    current_stock INTEGER NOT NULL CHECK (current_stock >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ON_SALE', 'PAUSED', 'SOLD_OUT')),
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, display_order),
    CONSTRAINT chk_products_stock_range CHECK (current_stock <= initial_stock)
);

CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    student_id UUID NOT NULL REFERENCES students(id),
    product_id UUID NOT NULL REFERENCES products(id),
    transaction_id UUID NOT NULL UNIQUE REFERENCES financial_transactions(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    total_price BIGINT NOT NULL CHECK (total_price > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'REJECTED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_room_status_order
    ON products(room_id, status, display_order);
CREATE INDEX idx_purchases_room_created
    ON purchases(room_id, created_at DESC);
CREATE INDEX idx_purchases_student_created
    ON purchases(student_id, created_at DESC);
