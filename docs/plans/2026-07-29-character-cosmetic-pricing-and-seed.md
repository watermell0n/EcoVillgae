# 캐릭터 코스메틱 — 가격 밸런싱 & 시드 규격

- 기준일: 2026-07-29
- 상위 문서: [character-cosmetic-module-plan](2026-07-29-character-cosmetic-module-plan.md)

## 1. 경제 전제

- 역할 소득 100비빔/일, 2일 진행. 1일 참여수당 10 + 정답당 10, 2일 참여수당 10 + 정답당 5.
- 학생당 확보 가능 화폐 대략 **200~300비빔**. → 코스메틱은 2일 동안 **2~5개** 살 수 있는 수준으로 가격을 잡아 소비 선택·기회비용을 학습하게 한다.
- 소득세·예금·투자 등 다른 소비처와 경쟁하므로 코스메틱만으로 지갑이 순식간에 마르지 않게 한다.

## 2. 슬롯별 가격대 기준표

| 슬롯 | 가격대(비빔) | 근거 |
| --- | --- | --- |
| 얼굴·목·손 소품 | 15~30 (특수 40) | 진입 소품, 부담 적게 |
| 등 장식·효과 | 30~50 | 실루엣 변화가 커 중상단 |
| 신발 | 25~40 | 중간대 |
| 모자 | 20~50 (프리미엄 80) | 눈에 띄는 포인트. 왕관은 프리미엄 |
| 상의 | 35~65 | 비주얼 변화 가장 큼 → 상단대 |
| 배경 | 40~55 | 전체 분위기 전환 |

가격은 10비빔 단위 반올림 권장(정산·거스름 단순화).

## 3. 시드 목록 (slot · sprite_key · price)

`BODY_VARIANT` 상품은 체형별 4버전이므로 렌더 시 `sprite_key + '_' + body_state`로 해석한다.
기본 대상은 TOP/BOTTOM/SHOES이며, 몸을 감싸는 모자·목도리·튜브·공룡 꼬리는 예외적으로 `BODY_VARIANT`를 사용한다.
`COMMON` 상품은 공용 에셋을, `POSITION_VARIANT` 상품은 공용 에셋과 체형별 정수 위치 보정을 사용한다.
상품은 한 번만 구매하며 체형이 바뀌면 같은 상품 ID로 대응 에셋이 자동 선택된다.
모든 상품은 `BASIC/SEASONAL/SPORTS/CAREER/FANTASY/EVENT` 중 하나의 주 테마를 가진다.

### HAT (21) — 기본 공용, 밀착형 4종은 체형별 에셋
| 이름 | sprite_key | price |
| --- | --- | --- |
| 빨간 캡 | hat_red_cap | 30 |
| 파티모자 | hat_party | 20 |
| 왕관 | hat_crown | 80 |
| 밀짚모자 | hat_straw | 25 |
| 비니 | hat_beanie | 25 |
| 비치 모자 | hat_beach | 30 |
| 방울 비니 | hat_pom_beanie | 30 |
| 마법사 모자 | hat_wizard | 40 |
| 요리사 모자 | hat_chef | 30 |
| 탐험가 모자 | hat_safari | 35 |
| 야구 모자(파랑) | hat_baseball_blue | 30 |
| 야구 모자(빨강) | hat_baseball_red | 30 |
| 야구 모자(남색) | hat_baseball_navy | 30 |
| 야구 모자(초록) | hat_baseball_green | 30 |
| 야구 모자(노랑) | hat_baseball_yellow | 30 |
| 우주 헬멧 | hat_space_helmet | 45 |
| 기사 투구 | hat_knight | 40 |
| 공룡 후드 | hat_dinosaur | 35 |
| 안전 경찰 모자 | hat_safety_police | 30 |
| 파일럿 모자 | hat_pilot | 35 |
| 면사포 | hat_veil | 30 |

### TOP (22) — 체형별 에셋
| 이름 | sprite_key | price |
| --- | --- | --- |
| 파란 티셔츠 | top_blue_tee | 40 |
| 노란 후드 | top_yellow_hoodie | 50 |
| 초록 조끼 | top_green_vest | 35 |
| 멜빵바지 | top_overalls | 45 |
| 수영복 | top_swimsuit | 40 |
| 패딩 점퍼 | top_padding | 55 |
| 정장 | top_suit | 60 |
| 마법사 로브 | top_wizard_robe | 55 |
| 한복 | top_hanbok | 65 |
| 축구 유니폼 | top_soccer | 45 |
| 야구 유니폼(핀스트라이프) | top_baseball_pinstripe | 55 |
| 야구 유니폼(빨강) | top_baseball_red | 50 |
| 야구 유니폼(파랑) | top_baseball_blue | 50 |
| 야구 유니폼(남색) | top_baseball_navy | 50 |
| 야구 유니폼(원정 회색) | top_baseball_gray | 50 |
| 우주복 | top_spacesuit | 60 |
| 판타지 갑옷 | top_fantasy_armor | 55 |
| 공룡 옷 | top_dinosaur | 55 |
| 안전 경찰복 | top_safety_police | 50 |
| 법조인 정장 | top_legal_professional | 55 |
| 파일럿 제복 | top_pilot | 55 |
| 웨딩드레스 | top_wedding_dress | 65 |

### SHOES (6) — 체형별 에셋
| 이름 | sprite_key | price |
| --- | --- | --- |
| 운동화 | shoes_sneakers | 30 |
| 빨간 장화 | shoes_red_boots | 35 |
| 샌들 | shoes_sandals | 25 |
| 아쿠아슈즈 | shoes_aqua | 25 |
| 스노우 부츠 | shoes_snow_boots | 40 |
| 우주 부츠 | shoes_space_boots | 40 |

### 세부 액세서리 슬롯 (18)

| 슬롯 | 상품 |
| --- | --- |
| FACE (4) | 선글라스 20 · 물안경 20 · 스키 고글 30 · 헤드폰 40 |
| NECK (4) | 목도리 25 · 나비넥타이 15 · 니트 목도리 25 · 목걸이 25 |
| BACK (3) | 배낭 45 · 요정 날개 50 · 공룡 꼬리 30 |
| HAND (5) | 야구 글러브 30 · 별빛 마법봉 30 · 장난감 검 30 · 서류 가방 30 · 꽃다발 25 |
| EFFECT (2) | 튜브 35 · 몽실 구름 45 |

### BACKGROUND (5)
| 이름 | sprite_key | price |
| --- | --- | --- |
| 봄 벚꽃 | bg_spring | 45 |
| 여름 바다 | bg_summer_sea | 50 |
| 가을 단풍 | bg_autumn | 45 |
| 겨울 눈 | bg_winter | 50 |
| 우주 | bg_space | 55 |

> 밤하늘·공원·무지개·도시·숲·사막·무대·바닷속은 판매 종료 상태로 보존한다.

### 야구 상품 (11) — 실제 구단 로고·구단명 미사용, 팀 컬러만 차용한 오리지널
- 모자 5: hat_baseball_blue/red/navy/green/yellow (각 30)
- 유니폼 5(체형별 4버전): top_baseball_pinstripe 55 · red/blue/navy/gray 50
- 손 소품 1: hand_baseball_glove 30

합계 77종 (모자21 · 얼굴4 · 목4 · 상의22 · 하의5 · 신발6 · 등3 · 손5 · 효과2 · 배경5).

### 테마 컬렉션

| 테마 | 대표 구성 |
| --- | --- |
| BASIC | 빨간 캡, 비니, 티셔츠, 후드, 조끼, 멜빵바지, 운동화, 선글라스, 헤드폰, 배낭 |
| SEASONAL | 밀짚·비치·방울 비니, 수영복, 패딩, 계절 신발·장식·배경 |
| SPORTS | 축구·야구 모자/유니폼, 야구 글러브 |
| CAREER | 요리사, 탐험가, 우주비행사, 안전 경찰, 법조인, 파일럿 |
| FANTASY | 왕관, 마법사, 판타지 검사, 공룡, 요정 날개, 몽실 구름 |
| EVENT | 파티, 정장, 한복, 웨딩드레스, 면사포, 꽃다발 |

테마는 필터와 컬렉션 진행률에만 사용하며, 세트는 개별 구매한다. 완성 보상은 화폐가 아닌 포즈·프레임으로 제공한다.

## 4. 체형 판정(이스터에그) 규격

- 입력: `wallet_balance`, 누적 코스메틱+상점 소비 `spend_total`, 기준 소득 `income_base`(역할 소득 100).
- 지표: `save_ratio = balance / (income_base * days)`, `spend_ratio = spend_total / (income_base * days)`.
- 판정(4단계): `save_ratio ≥ 0.6` → CHUBBY / 그 외 저축 우세 → (BASE 상단) / 균형 → BASE / `spend_ratio ≥ 0.6` → SLIM / `spend_ratio ≥ 0.9` → VERY_SLIM. (SLIM↔VERY_SLIM은 미세 차이)
- 히스테리시스: 경계에서 ±0.1 여유를 둬 잦은 체형 전환 방지.
- 얼굴은 몸의 20~30%만 변화(볼·턱선만). 머리 상단·눈·입·전체 키·모자 앵커 고정. 상세는 plan 문서 §10.
- 노출: 본인만(비공개), 교사 토글로 off 가능. UI 표현은 중립·긍정어.

## 5. 에셋 좌표 기준

- 기본 캐릭터 정본: [`base-character-4-body-refined-v2.png`](../assets/character/base-character-4-body-refined-v2.png)
- 체형별 착용 정본: [`body-aware-outfit-fitting-v1.png`](../assets/character/body-aware-outfit-fitting-v1.png)
- 제작 원본은 투명 64×72px, 상점 미리보기는 nearest 방식 4배 확대(256×288px)한다.
- 모든 레이어는 같은 캔버스를 유지하고 `(0, 0)`에서 합성한다.
- 모자 `(32,5)`, 얼굴 `(32,24)`, 목 `(32,31)`, 몸통 `(32,44)`, 허리 `(32,51)`, 바닥 `(32,67)`을 기준으로 한다.
- 등에 매는 검·마법봉은 `(36,38)`, 옆 소품은 `(51,45)`, 꽃다발은 `(47,51)`을 기준으로 체형별 정수 좌표를 보정한다.
- 체형별 제작 여부, 예외 상품, 파일명은 `2026-07-29-character-cosmetic-asset-spec.md`를 단일 기준으로 사용한다.
