-- 종목마다 하루에 오르내릴 수 있는 범위를 저장한다. 교사가 정하고 학생 화면에 그대로
-- 보여 주므로, 가격 단계에서 되계산하지 않고 종목에 함께 둔다.
ALTER TABLE investment_assets
    ADD COLUMN min_return_bps integer NOT NULL DEFAULT 0,
    ADD COLUMN max_return_bps integer NOT NULL DEFAULT 0;

ALTER TABLE investment_assets
    ADD CONSTRAINT ck_investment_assets_return_band
        CHECK (min_return_bps <= max_return_bps);
