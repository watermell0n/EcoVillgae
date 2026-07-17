CREATE TABLE deposits (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    session_id UUID NOT NULL REFERENCES sessions(id),
    student_id UUID NOT NULL REFERENCES students(id),
    transaction_id UUID UNIQUE REFERENCES financial_transactions(id),
    principal BIGINT NOT NULL CHECK (principal >= 0),
    interest_rate_bps INTEGER NOT NULL CHECK (interest_rate_bps >= 0),
    expected_interest BIGINT NOT NULL CHECK (expected_interest >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    UNIQUE (session_id, student_id)
);

CREATE TABLE investment_assets (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    risk_level VARCHAR(20) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    min_return_bps INTEGER NOT NULL,
    max_return_bps INTEGER NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, code),
    UNIQUE (room_id, display_order),
    CHECK (min_return_bps <= max_return_bps)
);

CREATE TABLE investment_scenarios (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL UNIQUE REFERENCES rooms(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('READY', 'LOCKED')),
    locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE investment_scenario_returns (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES investment_scenarios(id),
    asset_id UUID NOT NULL REFERENCES investment_assets(id),
    return_bps INTEGER NOT NULL,
    UNIQUE (scenario_id, asset_id)
);

CREATE TABLE investment_orders (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    session_id UUID NOT NULL REFERENCES sessions(id),
    student_id UUID NOT NULL REFERENCES students(id),
    scenario_id UUID REFERENCES investment_scenarios(id),
    transaction_id UUID UNIQUE REFERENCES financial_transactions(id),
    total_amount BIGINT NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    UNIQUE (session_id, student_id)
);

CREATE TABLE investment_allocations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES investment_orders(id),
    asset_id UUID NOT NULL REFERENCES investment_assets(id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (order_id, asset_id)
);

CREATE INDEX idx_deposits_room_status ON deposits(room_id, status);
CREATE INDEX idx_investment_orders_room_status ON investment_orders(room_id, status);
CREATE INDEX idx_investment_allocations_order ON investment_allocations(order_id);
