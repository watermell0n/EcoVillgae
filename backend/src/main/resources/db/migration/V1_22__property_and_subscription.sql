-- Classroom-seat real estate, lease contracts, and housing subscription accounts.

CREATE TABLE property_layouts (
    classroom_id uuid PRIMARY KEY,
    row_count integer NOT NULL,
    column_count integer NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_property_layout_dimensions CHECK (
        row_count BETWEEN 1 AND 26 AND column_count BETWEEN 1 AND 26
    ),
    CONSTRAINT fk_property_layout_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE properties (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    coordinate varchar(12) NOT NULL,
    display_name varchar(120) NOT NULL,
    row_index integer NOT NULL,
    column_index integer NOT NULL,
    property_tier varchar(20) NOT NULL DEFAULT 'GENERAL',
    is_public boolean NOT NULL DEFAULT true,
    is_active boolean NOT NULL DEFAULT true,
    allows_rent boolean NOT NULL DEFAULT true,
    allows_jeonse boolean NOT NULL DEFAULT true,
    allows_sale boolean NOT NULL DEFAULT true,
    allows_subscription boolean NOT NULL DEFAULT false,
    sale_price bigint NOT NULL DEFAULT 0,
    deposit_amount bigint NOT NULL DEFAULT 0,
    monthly_rent bigint NOT NULL DEFAULT 0,
    rent_payment_cycle_days integer NOT NULL DEFAULT 7,
    contract_term_days integer NOT NULL DEFAULT 28,
    owner_kind varchar(20) NOT NULL DEFAULT 'TEACHER',
    owner_student_id uuid,
    subscription_offer_id uuid,
    subscription_winner_student_id uuid,
    status varchar(40) NOT NULL DEFAULT 'AVAILABLE',
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_properties_scope UNIQUE (classroom_id, id),
    CONSTRAINT ck_properties_values CHECK (
        row_index >= 1 AND column_index >= 1
        AND sale_price >= 0 AND deposit_amount >= 0 AND monthly_rent >= 0
        AND rent_payment_cycle_days BETWEEN 1 AND 365
        AND contract_term_days BETWEEN 1 AND 365
        AND property_tier IN ('GENERAL', 'POPULAR')
        AND owner_kind IN ('TEACHER', 'STUDENT')
        AND status IN ('TEACHER_OWNED', 'AVAILABLE', 'RENTED', 'STUDENT_OWNED',
                       'LISTED_FOR_RENT', 'SUBSCRIPTION_OPEN', 'INACTIVE')
        AND (owner_kind = 'TEACHER' AND owner_student_id IS NULL
             OR owner_kind = 'STUDENT' AND owner_student_id IS NOT NULL)
    ),
    CONSTRAINT fk_properties_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_properties_owner_student FOREIGN KEY (classroom_id, owner_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE property_lease_listings (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    property_id uuid NOT NULL,
    landlord_student_id uuid NOT NULL,
    lease_type varchar(20) NOT NULL,
    deposit_amount bigint NOT NULL DEFAULT 0,
    monthly_rent bigint NOT NULL DEFAULT 0,
    payment_cycle_days integer NOT NULL DEFAULT 7,
    contract_term_days integer NOT NULL DEFAULT 28,
    is_public boolean NOT NULL DEFAULT true,
    status varchar(30) NOT NULL DEFAULT 'ACTIVE',
    state_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_property_lease_listings_scope UNIQUE (classroom_id, id),
    CONSTRAINT ck_property_lease_listings_values CHECK (
        lease_type IN ('RENT', 'JEONSE')
        AND deposit_amount >= 0 AND monthly_rent >= 0
        AND payment_cycle_days BETWEEN 1 AND 365
        AND contract_term_days BETWEEN 1 AND 365
        AND status IN ('ACTIVE', 'INACTIVE', 'CLOSED')
    ),
    CONSTRAINT fk_property_lease_listings_property FOREIGN KEY (classroom_id, property_id)
        REFERENCES properties (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_lease_listings_landlord FOREIGN KEY (classroom_id, landlord_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE subscription_offers (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    title varchar(120) NOT NULL,
    application_opens_at timestamptz NOT NULL,
    application_closes_at timestamptz NOT NULL,
    sale_price bigint NOT NULL,
    supply_quantity integer NOT NULL,
    minimum_deposit_count integer NOT NULL DEFAULT 0,
    minimum_balance bigint NOT NULL DEFAULT 0,
    draw_method varchar(20) NOT NULL DEFAULT 'RANDOM',
    status varchar(30) NOT NULL DEFAULT 'OPEN',
    drawn_at timestamptz,
    drawn_by_user_id uuid,
    state_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_offers_scope UNIQUE (classroom_id, id),
    CONSTRAINT ck_subscription_offers_values CHECK (
        application_closes_at > application_opens_at
        AND sale_price > 0 AND supply_quantity > 0
        AND minimum_deposit_count >= 0 AND minimum_balance >= 0
        AND draw_method IN ('RANDOM')
        AND status IN ('OPEN', 'CLOSED', 'DRAWN', 'CANCELED')
    ),
    CONSTRAINT fk_subscription_offers_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscription_offers_drawer FOREIGN KEY (drawn_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

ALTER TABLE properties ADD CONSTRAINT fk_properties_subscription_offer
    FOREIGN KEY (classroom_id, subscription_offer_id)
    REFERENCES subscription_offers (classroom_id, id) ON DELETE RESTRICT;

CREATE TABLE subscription_offer_properties (
    classroom_id uuid NOT NULL,
    subscription_offer_id uuid NOT NULL,
    property_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (subscription_offer_id, property_id),
    CONSTRAINT fk_subscription_offer_properties_offer FOREIGN KEY (classroom_id, subscription_offer_id)
        REFERENCES subscription_offers (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscription_offer_properties_property FOREIGN KEY (classroom_id, property_id)
        REFERENCES properties (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE property_contracts (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    property_id uuid NOT NULL,
    lease_listing_id uuid,
    subscription_offer_id uuid,
    subscription_application_id uuid,
    contract_type varchar(20) NOT NULL,
    occupant_student_id uuid NOT NULL,
    landlord_kind varchar(20) NOT NULL,
    landlord_student_id uuid,
    deposit_amount bigint NOT NULL DEFAULT 0,
    monthly_rent bigint NOT NULL DEFAULT 0,
    sale_price bigint NOT NULL DEFAULT 0,
    payment_cycle_days integer,
    contract_term_days integer,
    started_at timestamptz,
    ends_at timestamptz,
    next_payment_at timestamptz,
    approved_by_user_id uuid,
    decision_reason text,
    approved_at timestamptz,
    terminated_at timestamptz,
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_property_contracts_scope UNIQUE (classroom_id, id),
    CONSTRAINT ck_property_contracts_values CHECK (
        contract_type IN ('RENT', 'JEONSE', 'SALE')
        AND landlord_kind IN ('TEACHER', 'STUDENT')
        AND deposit_amount >= 0 AND monthly_rent >= 0 AND sale_price >= 0
        AND (payment_cycle_days IS NULL OR payment_cycle_days BETWEEN 1 AND 365)
        AND (contract_term_days IS NULL OR contract_term_days BETWEEN 1 AND 365)
        AND status IN ('PENDING', 'ACTIVE', 'OVERDUE', 'COMPLETED', 'REJECTED', 'TERMINATED')
        AND (landlord_kind = 'TEACHER' AND landlord_student_id IS NULL
             OR landlord_kind = 'STUDENT' AND landlord_student_id IS NOT NULL)
    ),
    CONSTRAINT fk_property_contracts_property FOREIGN KEY (classroom_id, property_id)
        REFERENCES properties (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_contracts_occupant FOREIGN KEY (classroom_id, occupant_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_contracts_landlord FOREIGN KEY (classroom_id, landlord_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_contracts_listing FOREIGN KEY (classroom_id, lease_listing_id)
        REFERENCES property_lease_listings (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_contracts_offer FOREIGN KEY (classroom_id, subscription_offer_id)
        REFERENCES subscription_offers (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_contracts_approver FOREIGN KEY (approved_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE property_contract_payments (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    property_contract_id uuid NOT NULL,
    due_at timestamptz NOT NULL,
    amount bigint NOT NULL,
    paid_at timestamptz,
    transaction_group_id uuid,
    status varchar(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_property_contract_payments_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_property_contract_payments_due UNIQUE (property_contract_id, due_at),
    CONSTRAINT ck_property_contract_payments_values CHECK (
        amount > 0 AND status IN ('SCHEDULED', 'PAID', 'MISSED')
    ),
    CONSTRAINT fk_property_contract_payments_contract FOREIGN KEY (classroom_id, property_contract_id)
        REFERENCES property_contracts (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_contract_payments_group FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE subscription_accounts (
    classroom_student_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    total_deposit_amount bigint NOT NULL DEFAULT 0,
    deposit_count integer NOT NULL DEFAULT 0,
    last_deposited_at timestamptz,
    auto_deposit_amount bigint,
    auto_deposit_cycle_days integer,
    next_auto_deposit_at timestamptz,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    state_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_accounts_values CHECK (
        total_deposit_amount >= 0 AND deposit_count >= 0
        AND (auto_deposit_amount IS NULL OR auto_deposit_amount > 0)
        AND (auto_deposit_cycle_days IS NULL OR auto_deposit_cycle_days BETWEEN 1 AND 365)
        AND status IN ('ACTIVE', 'CLOSED')
    ),
    CONSTRAINT fk_subscription_accounts_student FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE subscription_auto_deposit_policies (
    classroom_id uuid PRIMARY KEY,
    enabled boolean NOT NULL DEFAULT false,
    amount bigint,
    cycle_days integer,
    state_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_auto_deposit_policies_values CHECK (
        (enabled = false AND amount IS NULL AND cycle_days IS NULL)
        OR (enabled = true AND amount > 0 AND cycle_days BETWEEN 1 AND 365)
    ),
    CONSTRAINT fk_subscription_auto_deposit_policies_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE subscription_account_deposits (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    amount bigint NOT NULL,
    deposited_at timestamptz NOT NULL,
    deposit_type varchar(20) NOT NULL,
    transaction_group_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_account_deposits_scope UNIQUE (classroom_id, id),
    CONSTRAINT ck_subscription_account_deposits_values CHECK (
        amount > 0 AND deposit_type IN ('MANUAL', 'AUTO', 'REFUND')
    ),
    CONSTRAINT fk_subscription_account_deposits_account FOREIGN KEY (classroom_student_id)
        REFERENCES subscription_accounts (classroom_student_id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscription_account_deposits_group FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE subscription_applications (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    subscription_offer_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    deposit_count_snapshot integer NOT NULL,
    deposit_amount_snapshot bigint NOT NULL,
    assigned_property_id uuid,
    status varchar(20) NOT NULL DEFAULT 'APPLIED',
    applied_at timestamptz NOT NULL,
    drawn_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_applications_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_subscription_applications_student_offer UNIQUE (subscription_offer_id, classroom_student_id),
    CONSTRAINT ck_subscription_applications_values CHECK (
        deposit_count_snapshot >= 0 AND deposit_amount_snapshot >= 0
        AND status IN ('APPLIED', 'WON', 'LOST', 'PURCHASED', 'CANCELED')
    ),
    CONSTRAINT fk_subscription_applications_offer FOREIGN KEY (classroom_id, subscription_offer_id)
        REFERENCES subscription_offers (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscription_applications_student FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscription_applications_property FOREIGN KEY (classroom_id, assigned_property_id)
        REFERENCES properties (classroom_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_properties_available ON properties (classroom_id, status)
    WHERE is_active AND is_public;
CREATE UNIQUE INDEX ux_properties_active_coordinate
    ON properties (classroom_id, coordinate)
    WHERE is_active;
CREATE INDEX ix_property_contracts_due ON property_contracts (classroom_id, next_payment_at)
    WHERE status IN ('ACTIVE', 'OVERDUE');
CREATE INDEX ix_property_contract_payments_due ON property_contract_payments (classroom_id, due_at)
    WHERE status IN ('SCHEDULED', 'MISSED');
CREATE INDEX ix_subscription_offers_open ON subscription_offers (classroom_id, application_closes_at)
    WHERE status = 'OPEN';
