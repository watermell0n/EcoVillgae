-- Official store, character catalog, inventory, and teacher-approved peer trades.

CREATE TABLE store_products (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    description text,
    product_kind varchar(40) NOT NULL,
    created_by_user_id uuid NOT NULL,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_products_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_store_products_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_products_creator FOREIGN KEY (created_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE store_product_versions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    store_product_id uuid NOT NULL,
    version integer NOT NULL,
    product_kind varchar(40) NOT NULL,
    unit_price bigint NOT NULL,
    vat_rate_bps integer NOT NULL,
    purchasable boolean NOT NULL DEFAULT true,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    published_at timestamptz NOT NULL,
    retired_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_product_versions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_store_product_versions_version UNIQUE (store_product_id, version),
    CONSTRAINT fk_store_product_versions_product
        FOREIGN KEY (classroom_id, store_product_id)
        REFERENCES store_products (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE store_stocks (
    store_product_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    available_quantity integer NOT NULL DEFAULT 0,
    reserved_quantity integer NOT NULL DEFAULT 0,
    state_version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_stocks_scope UNIQUE (classroom_id, store_product_id),
    CONSTRAINT fk_store_stocks_product
        FOREIGN KEY (classroom_id, store_product_id)
        REFERENCES store_products (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE store_orders (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    subtotal_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    total_amount bigint NOT NULL,
    policy_snapshot_id uuid NOT NULL,
    transaction_group_id uuid,
    received_at timestamptz,
    operation_canceled_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_orders_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_store_orders_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_orders_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_orders_policy_snapshot
        FOREIGN KEY (classroom_id, policy_snapshot_id)
        REFERENCES policy_snapshots (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_orders_transaction_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE store_order_items (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    store_order_id uuid NOT NULL,
    store_product_version_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_price bigint NOT NULL,
    vat_rate_bps integer NOT NULL,
    subtotal_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    total_amount bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_order_items_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_store_order_items_product UNIQUE (store_order_id, store_product_version_id),
    CONSTRAINT fk_store_order_items_order
        FOREIGN KEY (classroom_id, store_order_id)
        REFERENCES store_orders (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_order_items_product_version
        FOREIGN KEY (classroom_id, store_product_version_id)
        REFERENCES store_product_versions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE character_catalog (
    id uuid PRIMARY KEY,
    character_code varchar(80) NOT NULL,
    name varchar(120) NOT NULL,
    source_title varchar(200) NOT NULL,
    source_url text NOT NULL,
    rights_status varchar(40) NOT NULL,
    rights_evidence_key varchar(512) NOT NULL,
    reviewed_at timestamptz,
    status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_character_catalog_code UNIQUE (character_code)
);

CREATE TABLE classroom_characters (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    character_catalog_id uuid NOT NULL,
    store_product_id uuid,
    display_name varchar(120) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_classroom_characters_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_classroom_characters_catalog UNIQUE (classroom_id, character_catalog_id),
    CONSTRAINT fk_classroom_characters_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_classroom_characters_catalog FOREIGN KEY (character_catalog_id)
        REFERENCES character_catalog (id) ON DELETE RESTRICT,
    CONSTRAINT fk_classroom_characters_product
        FOREIGN KEY (classroom_id, store_product_id)
        REFERENCES store_products (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE trades (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    proposer_student_id uuid NOT NULL,
    counterpart_student_id uuid NOT NULL,
    description text,
    policy_snapshot_id uuid,
    transaction_group_id uuid,
    counterpart_decided_at timestamptz,
    teacher_decided_at timestamptz,
    expires_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_trades_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_trades_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_trades_proposer
        FOREIGN KEY (classroom_id, proposer_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trades_counterpart
        FOREIGN KEY (classroom_id, counterpart_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trades_policy_snapshot
        FOREIGN KEY (classroom_id, policy_snapshot_id)
        REFERENCES policy_snapshots (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trades_transaction_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE inventory_items (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    character_catalog_id uuid NOT NULL,
    source_order_item_id uuid,
    locked_trade_id uuid,
    equipped_at timestamptz,
    acquired_at timestamptz NOT NULL DEFAULT now(),
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_items_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_inventory_items_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_catalog FOREIGN KEY (character_catalog_id)
        REFERENCES character_catalog (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_source_order_item
        FOREIGN KEY (classroom_id, source_order_item_id)
        REFERENCES store_order_items (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_items_locked_trade
        FOREIGN KEY (classroom_id, locked_trade_id)
        REFERENCES trades (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE trade_money_legs (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    trade_id uuid NOT NULL,
    from_student_id uuid NOT NULL,
    to_student_id uuid NOT NULL,
    amount bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_trade_money_legs_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_trade_money_legs_direction UNIQUE (trade_id, from_student_id, to_student_id),
    CONSTRAINT fk_trade_money_legs_trade
        FOREIGN KEY (classroom_id, trade_id)
        REFERENCES trades (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_money_legs_from_student
        FOREIGN KEY (classroom_id, from_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_money_legs_to_student
        FOREIGN KEY (classroom_id, to_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE trade_item_legs (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    trade_id uuid NOT NULL,
    inventory_item_id uuid NOT NULL,
    from_student_id uuid NOT NULL,
    to_student_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_trade_item_legs_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_trade_item_legs_item UNIQUE (trade_id, inventory_item_id),
    CONSTRAINT fk_trade_item_legs_trade
        FOREIGN KEY (classroom_id, trade_id)
        REFERENCES trades (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_item_legs_inventory_item
        FOREIGN KEY (classroom_id, inventory_item_id)
        REFERENCES inventory_items (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_item_legs_from_student
        FOREIGN KEY (classroom_id, from_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_item_legs_to_student
        FOREIGN KEY (classroom_id, to_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE trade_decisions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    trade_id uuid NOT NULL,
    decision_type varchar(40) NOT NULL,
    actor_type varchar(40) NOT NULL,
    actor_id uuid NOT NULL,
    decision varchar(40) NOT NULL,
    reason text,
    decided_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_trade_decisions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_trade_decisions_type UNIQUE (trade_id, decision_type),
    CONSTRAINT fk_trade_decisions_trade
        FOREIGN KEY (classroom_id, trade_id)
        REFERENCES trades (classroom_id, id) ON DELETE RESTRICT
);
