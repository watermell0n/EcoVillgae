-- 계약 신청 시점에 학생 지갑에서 이미 돈을 옮긴 계약을 표시한다.
-- 값이 비어 있으면 예전 방식(교사 승인 시점 정산) 계약이라, 승인할 때 돈을 옮긴다.
ALTER TABLE property_contracts ADD COLUMN paid_at timestamptz;
