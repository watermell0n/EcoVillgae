ALTER TABLE investment_assets
    ADD COLUMN company_info TEXT;

UPDATE investment_assets
SET name = '거북이 상사',
    description = '생활용품을 만드는 오래된 회사예요. 큰 변화는 적지만 매달 꾸준히 물건을 팔고 있어요.',
    company_info = $company$
거북이상사는 생활용품을 만드는 오래된 회사예요.
새롭고 화려한 상품은 적지만, 사람들이 매일 쓰는 물건을 꾸준히 팔고 있어요.

최근 한 달 동안 거북이상사의 판매량은 지난달보다 120건 늘었어요.
반품은 3건에서 1건으로 줄었고, 창고에 남아 있던 재고도 줄었어요.

또 학교와 사무실에서 사용하는 기본 문구 세트를 새로 납품하게 되었어요.
다만 새 상품을 기다리는 사람은 많지 않고, 뉴스에 크게 소개된 적도 없어요.

이번 달 거북이상사의 회사 가치는 어떻게 변할까요?
자료를 보고 직접 예상해보세요.
$company$,
    risk_level = 'LOW',
    min_return_bps = -1500,
    max_return_bps = 2500
WHERE display_order = 1;

UPDATE investment_assets
SET name = '독수리 테크',
    description = '새로운 학습 기기를 만드는 기술 회사예요. 성공하면 크게 성장할 수 있지만, 실패하면 손실도 클 수 있어요.',
    company_info = $company$
독수리테크는 새로운 AI 학습 기기를 만드는 회사예요.
최근 발표한 제품은 디자인이 멋지고, 체험 행사에서도 많은 관심을 받았어요.
예약 신청도 첫날 300건이나 들어왔어요.

하지만 제품을 만드는 데 필요한 부품 가격이 지난달보다 50원 올랐어요.
완성된 제품 100개 중 18개에서는 작동 오류가 발견되었어요.

원래 이번 주에 배송을 시작하려고 했지만,
추가 점검 때문에 배송이 일주일 늦어졌어요.
그래도 회사는 “더 좋은 제품을 보내기 위한 준비”라고 설명했어요.

이번 달 독수리테크의 회사 가치는 어떻게 변할까요?
좋은 소식과 걱정되는 소식을 함께 보고 예상해보세요.
$company$,
    risk_level = 'MEDIUM',
    min_return_bps = -3000,
    max_return_bps = 3000
WHERE display_order = 2;

UPDATE investment_assets
SET name = '호랑이 운송',
    description = '배송 성공 793건, 장거리 배송 150건, 대형 배송 101건! 하지만 경쟁 회사도 주문 처리 481건으로 만만치 않아요.',
    company_info = $company$
호랑이운송은 최근 86일 동안 793번 배달에 성공했어요.
경쟁 회사도 792번 성공해서 거의 비슷해요.

긴 거리 배달은 호랑이운송이 150번, 경쟁 회사가 134번 성공했어요.
큰 물건 배달도 호랑이운송이 101번, 경쟁 회사가 95번 성공했어요.
이 부분은 호랑이운송이 조금 앞서요.

하지만 주문 수는 경쟁 회사가 더 많아요.
호랑이운송은 450건, 경쟁 회사는 481건의 주문을 받았어요.
돈을 번 횟수도 호랑이운송은 431번, 경쟁 회사는 451번이에요.

실수 점수는 호랑이운송이 4.28점, 경쟁 회사가 4.50점이에요.
낮을수록 좋은 점수라서, 실수는 호랑이운송이 조금 적어요.

두 회사의 직접 대결은 호랑이운송이 5번 이기고 4번 졌어요.
조금 앞서지만 차이가 크지 않아요.
$company$,
    risk_level = 'HIGH',
    min_return_bps = -3000,
    max_return_bps = 3000
WHERE display_order = 3;

ALTER TABLE investment_assets
    ALTER COLUMN company_info SET NOT NULL;
