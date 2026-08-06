-- 학급은 단기 투자와 장기 투자를 동시에 운영한다. 지금까지는 교실당 시장이 하나여서
-- 한쪽 시나리오를 만들면 다른 쪽을 만들 수 없었다. 시장을 모드별로 하나씩 두어
-- 두 시나리오가 서로를 밀어내지 않게 한다.

ALTER TABLE investment_markets
    ADD COLUMN investment_mode varchar(20);

-- 이미 있는 시장은 활성 시나리오 버전의 모드를 그대로 물려받는다.
UPDATE investment_markets AS market
   SET investment_mode = scenario.investment_mode
  FROM investment_scenario_versions AS scenario
 WHERE scenario.id = market.active_scenario_version_id
   AND market.investment_mode IS NULL;

-- 시나리오를 붙이기 전의 초안 시장은 모듈 활성화 기본값과 같은 LONG_TERM으로 둔다.
UPDATE investment_markets
   SET investment_mode = 'LONG_TERM'
 WHERE investment_mode IS NULL;

ALTER TABLE investment_markets
    ALTER COLUMN investment_mode SET NOT NULL,
    ALTER COLUMN investment_mode SET DEFAULT 'LONG_TERM',
    ADD CONSTRAINT ck_investment_markets_mode
        CHECK (investment_mode IN ('SHORT_TERM', 'LONG_TERM'));

-- 시장의 모드와 그 시장이 쓰는 시나리오의 모드는 어긋나면 안 된다. 시나리오는 시장을
-- (classroom_id, investment_market_id)로 참조하므로 모드까지 함께 묶어 검사한다.
ALTER TABLE investment_markets
    ADD CONSTRAINT uq_investment_markets_mode_scope
        UNIQUE (classroom_id, investment_mode, id);

-- 교실당 한 시장이던 제약을 모드당 한 시장으로 바꾼다. 정산이 끝난 CLOSED 시장은
-- 여러 개 남을 수 있으므로 이전과 같이 제외한다.
DROP INDEX ux_investment_markets_open;

CREATE UNIQUE INDEX ux_investment_markets_open
    ON investment_markets (classroom_id, investment_mode)
    WHERE status <> 'CLOSED';
