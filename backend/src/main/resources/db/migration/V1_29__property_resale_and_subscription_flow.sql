-- Student-to-student seat resale and a complete classroom subscription flow.

ALTER TABLE properties DROP CONSTRAINT ck_properties_values;
ALTER TABLE properties ADD CONSTRAINT ck_properties_values CHECK (
    row_index >= 1 AND column_index >= 1
    AND sale_price >= 0 AND deposit_amount >= 0 AND monthly_rent >= 0
    AND rent_payment_cycle_days BETWEEN 1 AND 365
    AND contract_term_days BETWEEN 1 AND 365
    AND property_tier IN ('GENERAL', 'POPULAR')
    AND owner_kind IN ('TEACHER', 'STUDENT')
    AND status IN ('TEACHER_OWNED', 'AVAILABLE', 'RENTED', 'STUDENT_OWNED',
                   'LISTED_FOR_RENT', 'LISTED_FOR_SALE', 'SUBSCRIPTION_OPEN', 'INACTIVE')
    AND (owner_kind = 'TEACHER' AND owner_student_id IS NULL
         OR owner_kind = 'STUDENT' AND owner_student_id IS NOT NULL)
);

CREATE TABLE property_sale_listings (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    property_id uuid NOT NULL,
    seller_student_id uuid NOT NULL,
    sale_price bigint NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    state_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_property_sale_listings_scope UNIQUE (classroom_id, id),
    CONSTRAINT ck_property_sale_listings_values CHECK (
        sale_price > 0 AND status IN ('ACTIVE', 'CLOSED', 'PURCHASED')
    ),
    CONSTRAINT fk_property_sale_listings_property FOREIGN KEY (classroom_id, property_id)
        REFERENCES properties (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_property_sale_listings_seller FOREIGN KEY (classroom_id, seller_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_property_sale_listing_active_property
    ON property_sale_listings (property_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_property_sale_listings_classroom
    ON property_sale_listings (classroom_id, created_at DESC);
