ALTER TABLE investment_scenario_versions
    ADD COLUMN investment_mode varchar(20) NOT NULL DEFAULT 'LONG_TERM',
    ADD COLUMN scenario_source varchar(30) NOT NULL DEFAULT 'AI_GENERATED',
    ADD COLUMN reference_series_code varchar(40),
    ADD COLUMN reference_event_name varchar(120),
    ADD COLUMN reference_source_name varchar(120),
    ADD COLUMN reference_source_url varchar(500),
    ADD COLUMN reference_period_start date,
    ADD COLUMN reference_period_end date;

ALTER TABLE investment_assets
    ADD COLUMN fictional_company_profile varchar(1000)
        NOT NULL DEFAULT '학급 투자 활동을 위해 만든 가상 기업입니다.';

ALTER TABLE investment_scenario_versions
    ADD CONSTRAINT ck_investment_scenario_mode
        CHECK (investment_mode IN ('SHORT_TERM', 'LONG_TERM')),
    ADD CONSTRAINT ck_investment_scenario_source
        CHECK (scenario_source IN ('AI_GENERATED', 'HISTORICAL_INDEX')),
    ADD CONSTRAINT ck_investment_historical_reference
        CHECK (
            (
                scenario_source = 'HISTORICAL_INDEX'
                AND investment_mode = 'LONG_TERM'
                AND reference_series_code IS NOT NULL
                AND reference_event_name IS NOT NULL
                AND reference_source_name IS NOT NULL
                AND reference_source_url IS NOT NULL
                AND reference_period_start IS NOT NULL
                AND reference_period_end IS NOT NULL
                AND reference_period_start < reference_period_end
            )
            OR (
                scenario_source = 'AI_GENERATED'
                AND reference_series_code IS NULL
                AND reference_event_name IS NULL
                AND reference_source_name IS NULL
                AND reference_source_url IS NULL
                AND reference_period_start IS NULL
                AND reference_period_end IS NULL
            )
        );
