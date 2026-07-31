# FE 화면 생성 상세 스펙

- 작성일: 2026-07-30
- 목적: 백엔드 응답과 화면 생성기가 함께 참고할 수 있도록 화면별 데이터, 레이아웃, 색상, 상태, 금지 규칙을 구체화한다.
- 상위 가이드: `docs/design/FE_DESIGN_GUIDE.md`
- 화면 기준: `docs/SCREEN_SPEC.md`
- 기본 해상도: 학생 태블릿·공용 화면 `1280 x 800`, 교사 관리 웹 `1440 x 900`

## 1. 생성 공통 규칙

### 1.1 학생 화면 기본 프레임

학생 화면은 `마을 앱`처럼 보여야 한다. 기능 카드만 반복하는 대시보드가 아니라, 상단 상태와 중앙 활동 공간, 필요한 경우 오른쪽 요약 패널이 있는 구조를 사용한다.

```text
Top Dev Bar 또는 화면 그룹 바: 44~52px, nav-900
App Header: 72~80px, sky-100 -> mint-100 gradient
Main Area: pastel village gradient
Content: 32~46px margin, max width 1540px
```

| 영역 | 디자인 규칙 |
| --- | --- |
| 배경 | `sky-100`에서 `mint-100`, 하단 `cream-100`으로 흐르는 부드러운 그라데이션 |
| 헤더 | 좌측 서비스명, 중앙 현재 위치 pill, 우측 `일차`, `보유 비빔`, `학생` 상태 |
| 카드 | 흰색 `rgba(255,255,255,0.86)`, radius 18~24, 얇은 컬러 라인 |
| 텍스트 | 제목은 `ink-900`, 설명은 `muted-600`, 숫자와 결과는 굵게 |
| CTA | 주요 행동은 `orange-500`, 성공·입금은 `green-500`, 금융은 `purple-500` |
| 아이콘 | 이모지는 1개 보조 신호 정도로만 사용. 같은 카드 안에 2개 이상 남발 금지 |
| 캐릭터 | 별도 도트 PNG 슬롯만 제공. UI에서 캐릭터를 새로 만들지 않음 |

### 1.2 교사 관리 웹 기본 프레임

교사 화면은 학생용보다 더 차분하고 정보 밀도가 높아야 한다.

```text
Top Status Bar: 56~64px
Left Nav: 240~280px
Main Work Area: fluid
Right Summary Panel: 300~360px, 필요 화면에만 고정
```

| 영역 | 디자인 규칙 |
| --- | --- |
| 배경 | `#F6FAFC` 또는 연한 하늘색, 큰 장식 최소화 |
| 표 | 학생 관리, 거래 승인, 상품 목록은 표를 기본으로 사용 |
| 요약 패널 | 돈 이동, 정책 저장, 승인 화면에 고정 |
| CTA | 저장·승인 등 주요 버튼은 하늘색/민트, 위험·반려는 빨강 |
| 카드 | radius 8~12, 학생 화면보다 덜 둥글게 |

### 1.3 공용 화면 기본 프레임

공용 화면은 교실 앞 디스플레이 기준이다. 멀리서 읽히는 크기와 공개 범위가 가장 중요하다.

| 영역 | 디자인 규칙 |
| --- | --- |
| 배경 | 학생 화면과 같은 파스텔 톤, 장식 최소화 |
| 타이머 | 우상단 또는 중앙 상단에 크게 표시 |
| 본문 | 문제·선택지·집계를 한 화면에 크게 배치 |
| 폰트 | 제목 44px 이상, 선택지 30px 이상 |
| 개인정보 | 실명, 잔액, 세금, 신용, 개인 답안, TEST 순위 노출 금지 |

### 1.4 상태 공통

| 상태 | 표현 |
| --- | --- |
| `loading` | 카드 스켈레톤, CTA 비활성, 이전 값으로 성공처럼 보이지 않게 처리 |
| `empty` | 빈 화면 문구 + 다음 행동 1개 |
| `locked` | 흐린 카드, 잠금 사유 1줄, 교사 설정 필요 시 안내 |
| `processing` | 버튼 로딩, 중복 입력 방지 |
| `success` | 초록 결과 카드, 다음 행동 1개 |
| `failed` | 빨간 안내 카드, 원인·재시도 조건 분리 |
| `readOnly` | 변경 CTA 제거, 조회 가능 기간 또는 종료 안내 표시 |

### 1.5 생성 입력 계약

백엔드가 화면 생성기에 넘기는 데이터는 화면별 DTO와 공통 메타를 분리한다. 화면 생성기는 공통 메타로 라우팅과 권한을 결정하고, 화면 DTO로 실제 UI를 그린다.

```json
{
  "screenId": "SCR-STU-005",
  "role": "STU",
  "mode": "AUTONOMOUS",
  "classroom": {
    "id": "classroom-id",
    "name": "이코빌리지",
    "dayIndex": 1,
    "status": "ACTIVE",
    "currencyName": "비빔"
  },
  "studentView": {
    "nickname": "학생",
    "walletBalance": 158
  },
  "permissions": {
    "canView": true,
    "availableActions": ["CONFIRM_PAYSTUB"],
    "blockedReason": null
  },
  "data": {},
  "uiState": "paid",
  "lastSyncedAt": "2026-07-30T09:00:00+09:00"
}
```

| 필드 | 규칙 |
| --- | --- |
| `screenId` | 반드시 `docs/SCREEN_SPEC.md`의 화면 ID를 사용 |
| `role` | 학생 `STU`, 교사 `OWN/COT`, 공용 `PUBLIC_DISPLAY`처럼 화면 권한과 일치 |
| `mode` | 학생 앱은 `AUTONOMOUS/GUIDED/CLOSED/READ_ONLY` 중 하나 |
| `classroom` | 교실명, 일차, 상태, 화폐명을 모든 로그인 후 화면에 제공 |
| `studentView` | 학생 본인 화면에만 제공. 공용 화면 DTO에는 넣지 않음 |
| `permissions` | 버튼 렌더링의 유일한 기준. 화면에서 임의 판단하지 않음 |
| `data` | 화면별 상세 데이터. 없거나 늦게 오면 `loading` 또는 `empty` 상태 사용 |
| `uiState` | 화면 상태를 명시. 서버 상태와 다르면 서버 상태가 우선 |
| `lastSyncedAt` | 실패·오프라인·재시도 안내에 표시 가능 |

### 1.6 공통 컴포넌트 상세

#### AppHeader

| 속성 | 규칙 |
| --- | --- |
| 높이 | 학생 72~80px, 교사 56~64px, 공용 72px 이상 |
| 좌측 | 서비스명 또는 교실명. 학생 화면은 실명 표시 금지 |
| 중앙 | 현재 위치 pill. 예: `마을 광장`, `월급명세서`, `선생님 수업` |
| 우측 | 학생은 `일차`, `보유 비빔`, `학생`; 교사는 운영 상태·알림·계정 |
| 색 | 학생·공용 `sky-100 -> mint-100`, 교사 흰색 또는 매우 연한 하늘색 |

#### VillageCard

| 속성 | 규칙 |
| --- | --- |
| 크기 | 3열 기준 `min-height: 210px`, 카드 간격 22~26px |
| 구조 | 아이콘/에셋, 제목, 1줄 설명, 상태 배지 또는 CTA |
| 상태 | `active`, `locked`, `new`, `construction`, `resultOnly` |
| 색 | 모듈별 border만 다르게 하고 내부 배경은 흰색 계열 유지 |
| CTA | pill 형태, 폭 자동. 카드 전체 폭 버튼 금지 |

#### ReceiptCard

| 속성 | 규칙 |
| --- | --- |
| 사용 | 월급명세서, 구매 상세, 세금 안내 |
| 구조 | 상단 컬러 띠, 항목 행, 차감 행, 최종 금액 박스 |
| 색 | 상단 `orange-500`, 최종 금액 `green-500`, 차감 `red-500` |
| 숫자 | 금액은 항상 `비빔` 단위와 함께 표시 |
| 상태 | 지급 전, 지급 완료, 확인 완료, 오류 |

#### MoneySummary

| 속성 | 규칙 |
| --- | --- |
| 사용 | 지갑, 투자, 최종 리포트, 교사 경제 현황 |
| 구조 | 큰 금액 1개 + 변화량 2~4개 |
| 색 | 증가 초록, 감소 빨강, 중립 회색 |
| 금지 | 공용 화면에서 개인 금액 카드 사용 금지 |

#### DataTable

| 속성 | 규칙 |
| --- | --- |
| 사용 | 교사 학생 관리, 상품 관리, 승인함, 알림 |
| 행 높이 | 48~56px, 학생 이름·상태·금액·최근 활동을 한눈에 읽게 |
| 필터 | 표 위쪽에 검색, 상태, 기간 필터 배치 |
| 선택 상세 | 표 오른쪽 패널에 선택 행의 상세 표시 |
| 상태 | empty, loading, selected, bulkSelected, error |

#### CharacterSlot

| 속성 | 규칙 |
| --- | --- |
| 사용 | 학생 캐릭터, 보유 아이템, 공용 순위 공개 |
| 배경 | 흰색 또는 연한 하늘색. 도트가 선명하게 보이게 함 |
| 렌더링 | 구현 시 `image-rendering: pixelated` 적용 |
| 크기 | 학생 프로필 260~360px, 카드 썸네일 72~120px, 공용 순위 120~180px |
| 금지 | blur, glow, 과한 그림자, UI가 만든 임시 캐릭터 대체 |

#### ConfirmModal

| 속성 | 규칙 |
| --- | --- |
| 사용 | 구매, 거래, 예금, 투자 주문, 대출, 급여 지급, 승인 |
| 구조 | 영향 요약, 최종 금액, 변경 전/후, 취소·확인 버튼 |
| 색 | 확인은 주요 CTA, 취소는 outline, 위험은 빨강 |
| 데이터 | `requestId` 또는 생성 예정 요청번호를 표시 |
| 금지 | 영향 범위 없이 `확인` 버튼만 있는 모달 금지 |

### 1.7 레이아웃 밀도와 여백

| 화면 유형 | 레이아웃 | 여백·밀도 |
| --- | --- | --- |
| 학생 홈 | 3열 카드 그리드 | 좌우 32~46px, 카드 간격 22~26px, 첫 화면에 2행 일부 노출 |
| 학생 상세 | 1+1 또는 2열 | 왼쪽 핵심 카드, 오른쪽 설명·요약. 모바일 대응 시 세로 적층 |
| 학생 활동 | 좌측 목차 + 중앙 활동 + 우측 잠금/요약 | 중앙 활동 카드가 가장 커야 함 |
| 교사 목록 | 필터 + 표 + 우측 상세 | 표가 주인공, 카드형 반복 최소화 |
| 교사 설정 | stepper + 폼 + 변경 요약 | 저장 전 영향 요약 항상 표시 |
| 공용 진행 | 상단 상태 + 중앙 대형 콘텐츠 + 하단 안내 | 멀리서 읽히게 텍스트와 그래프를 크게 |

한 화면에 카드가 많을수록 모든 정보를 카드 안에 넣지 않는다. 화면 제목, 설명, 현재 위치, 보유 금액처럼 항상 보이는 정보는 배경 위 또는 헤더에 직접 배치한다.

### 1.8 텍스트 작성 규칙

| 구분 | 규칙 | 예시 |
| --- | --- | --- |
| 학생 제목 | 짧고 행동 중심 | `받은 돈과 세금을 확인해요` |
| 학생 설명 | 1~2줄, 쉬운 단어 | `내 지갑에 들어온 금액을 함께 살펴봐요.` |
| 교사 제목 | 업무 중심 | `개인거래 승인 대기` |
| 교사 설명 | 정책·영향 중심 | `승인 전 최신 잔액과 소유권을 다시 확인합니다.` |
| 오류 | 원인 + 다음 행동 | `재고가 부족해요. 다른 상품을 골라주세요.` |
| 잠금 | 이유 + 열리는 조건 | `선생님 수업이 끝나면 사용할 수 있어요.` |

금액은 `135비빔`처럼 숫자와 단위를 붙여 쓴다. 긴 정책 설명은 메인 카드가 아니라 도움말 또는 보조 notice로 분리한다.

### 1.9 생성 금지 패턴

| 금지 패턴 | 이유 | 대체 |
| --- | --- | --- |
| 모든 콘텐츠를 독립 카드로 감싸기 | 화면이 블록 나열처럼 보임 | 제목·설명·금액은 헤더/배경 위에 직접 배치 |
| 카드 안에 카드 넣기 | 계층이 흐려지고 답답함 | 구분선, 행, 얇은 배경 영역 사용 |
| 같은 이모지 반복 | 장난감처럼 보이고 정보 신호가 약해짐 | 화면 제목 1개 또는 카드 대표 아이콘 1개 |
| 실제 금융 서비스처럼 보이는 문구 | 초등 경제교육 목적과 어긋남 | `교육용`, `우리 반`, `비빔` 표현 유지 |
| 공용 화면에 개인 데이터 넣기 | 개인정보 원칙 위반 | 익명 집계와 공개 허용 결과만 사용 |
| 서버 성공 전 잔액 변경 표시 | 원장 커밋 전 오해 발생 | processing 후 서버 결과로 success 렌더링 |
| 외부 파일·외부 이미지 기준 문구 | 생성 근거를 repo에서 찾을 수 없음 | 이 문서와 `SCREEN_SPEC.md` 기준만 사용 |

## 2. 학생 화면 상세 스펙

### SCR-STU-001 학생 로그인

**목적**: 학생이 학급 코드와 개인 접속 정보로 경제교실에 들어간다.

**레이아웃**

- 전체 배경은 마을 배경 이미지 또는 `sky-100 -> mint-100 -> cream-100` 그라데이션.
- 중앙에 폭 480~560px 로그인 패널을 둔다.
- 패널 밖 배경에는 코인·별·구름 장식을 약하게 배치하되 입력 영역 주변은 비운다.
- 패널 상단은 집 아이콘, `이코빌리지`, `우리 반 경제생활이 시작되는 곳` 문구 순서.
- 입력은 세로 4개: 학급 코드, 출석번호, 닉네임, 비밀번호.
- CTA는 폭 100%, `마을 입장하기`, `orange-500`.

**색·컴포넌트**

- 패널 배경 `card-white`, radius 28, shadow 약하게.
- 입력 배경 `#F0F7FF`, border `#C9DFFD`.
- 로그인 실패는 입력 아래 `red-500`, 필드 border도 빨강.

**필요 데이터**

- `classroomCode`
- `studentNumber`
- `nickname`
- `password`
- `loginErrorCode`
- `availableEntryScreen`

**상태**

- `empty`: 필수값 없으면 CTA 비활성.
- `loading`: 버튼 `확인 중`으로 변경, 입력 잠금.
- `failed`: 계정 정보 불일치, 교실 종료, 잠긴 계정을 분리 표시.

**금지**

- 학생 실명 입력 요구 금지.
- 로그인 카드 안에 긴 서비스 설명 넣기 금지.

### SCR-STU-002 비밀번호 재설정

**목적**: 학생이 본인 확인 후 비밀번호만 재설정한다.

**레이아웃**

- 로그인 화면과 같은 배경과 패널 폭을 사용한다.
- 상단 제목은 `비밀번호 다시 만들기`.
- 입력 순서: 학급 코드, 출석번호, 닉네임, 새 비밀번호, 새 비밀번호 확인.
- 하단 보조 버튼 `로그인으로 돌아가기`.

**색·컴포넌트**

- 새 비밀번호 입력은 일반 입력과 같게, 오류만 빨강.
- 성공 상태는 초록 완료 카드로 전환하고 `로그인하기` CTA 표시.

**필요 데이터**

- `classroomCode`
- `studentNumber`
- `nickname`
- `newPassword`
- `confirmPassword`
- `resetTokenStatus`

**상태**

- `mismatch`: 비밀번호 확인 불일치.
- `invalidIdentity`: 본인 확인 실패.
- `success`: 재설정 완료.

**금지**

- 닉네임 변경 기능과 섞지 말 것.
- 선생님용 초기화 기능을 학생 화면에 노출하지 말 것.

### SCR-STU-003 경제교실 홈·상태 안내

**목적**: 학생이 현재 경제교실 상태와 사용할 수 있는 모듈을 확인하고 진입한다.

**레이아웃**

- App Header 아래에 현재 모드 안내를 작게 둔다. 예: `마을 광장`, `선생님 수업 중`, `읽기 전용`.
- 메인 영역은 3열 Village Card 그리드.
- 카드 위쪽에는 작은 마을 장식 라인을 둘 수 있으나 카드와 겹치지 않게 한다.
- 닫힌 상태에서는 상단에 종료 안내 배너를 넣고 카드 CTA를 제거한다.

**색·컴포넌트**

- 홈 배경은 `sky-100 -> mint-100 -> cream-100`.
- 카드별 얇은 border 색:
  - 상점 `orange-500`
  - 은행 `purple-500`
  - 투자 `red-500`
  - 월급 `green-500`
  - 공동기금 `yellow-500`
  - 신용 `sky-500`
- CTA는 작은 pill 버튼. 너무 큰 둥근 박스 반복 금지.

**필요 데이터**

- `classroomName`
- `dayIndex`
- `walletBalance`
- `appMode`
- `classroomStatus`
- `enabledModules[]`
- `moduleCards[]`: `id`, `title`, `subtitle`, `iconAsset`, `status`, `targetScreen`
- `announcement`

**상태**

- `AUTONOMOUS`: 활성 카드 표시.
- `GUIDED`: 지도 활동 카드 1개를 우선 표시하고 자유 활동은 잠금.
- `CLOSED`: 읽기 전용 홈, 과거 기록·리포트만 진입.
- `READ_ONLY`: 최종 리포트와 본인 기록만 표시.

**금지**

- 선택하지 않은 모듈을 비활성 카드로 남기지 말고 숨긴다.
- 한 화면을 카드 박스로만 가득 채우지 말고 헤더·배경 여백을 살린다.

### SCR-STU-004 지갑·거래내역

**목적**: 학생이 현재 잔액과 거래 흐름을 확인한다.

**레이아웃**

- 상단 중앙에 `내 지갑` 제목과 짧은 설명.
- 왼쪽 또는 상단에 큰 잔액 Hero Card: 현재 잔액, 오늘 변화량.
- 아래에는 거래 목록. 거래는 날짜별 그룹으로 묶는다.
- 오른쪽에는 `오늘 내 돈 요약` 패널을 둔다.

**색·컴포넌트**

- 잔액 Hero Card는 `green-500` 배경, 흰색 숫자.
- 입금은 초록, 출금·세금은 빨강, 보류는 보라.
- 거래 행은 흰색 위에 얇은 구분선. 과도한 카드 중첩 금지.

**필요 데이터**

- `walletBalance`
- `todayIncome`
- `todayExpense`
- `transactions[]`: `transactionId`, `type`, `title`, `amount`, `beforeBalance`, `afterBalance`, `createdAt`, `policySnapshotLabel`, `status`
- `hasMore`

**상태**

- `empty`: `아직 돈이 움직인 기록이 없어요`.
- `loading`: 잔액과 거래 목록 스켈레톤.
- `error`: 마지막 조회 시각과 다시 불러오기 버튼.

**금지**

- 다른 학생 잔액 노출 금지.
- 전후 잔액 없는 거래 상세 생성 금지.

### SCR-STU-005 월급명세서

**목적**: 학생이 받은 돈, 세금, 최종 입금액을 이해한다.

**레이아웃**

- 상단 중앙 제목 `받은 돈과 세금을 확인해요`.
- 2열 구성:
  - 왼쪽: 큰 Receipt Card
  - 오른쪽: 세금 설명 카드, 돈 흐름, 지갑 변화 요약
- 하단 오른쪽 또는 전체 폭 CTA `확인했어요`.

**색·컴포넌트**

- Receipt Card 상단 띠 `orange-500`.
- 총 받은 돈은 연한 크림 행, 세금은 연한 살구/빨강 행.
- 최종 입금액 박스는 초록 border와 큰 `Display` 숫자.
- 돈 흐름은 `총 받은 돈 -> 세금 -> 내 지갑` 3단계로 단순화.

**필요 데이터**

- `classroomName`
- `dayIndex`
- `grossSalary`
- `bonusAmount`
- `incomeTax`
- `deductionAmount`
- `netSalary`
- `walletBefore`
- `walletAfter`
- `taxDestinationLabel`
- `confirmedAt`

**상태**

- `notPaid`: 아직 지급 전 안내.
- `paid`: 확인 가능.
- `alreadyConfirmed`: CTA 대신 확인 완료 표시.
- `error`: 지급 내역을 찾을 수 없음.

**금지**

- 세금 설명을 긴 문단으로 넣지 말 것.
- 개인 세율·정책 원문을 그대로 노출하지 말고 학생용 문장으로 요약.

### SCR-STU-006 상점 목록

**목적**: 학생이 구매 가능한 상품을 탐색한다.

**레이아웃**

- 상단에 `이코 상점` 제목과 보유 비빔 pill.
- 카테고리 탭: 전체, 실제 상품, 디지털, 캐릭터.
- 상품은 3~4열 카드. 카드 이미지, 상품명, 최종 가격, 재고 상태, 상세 CTA.
- 실제 상품은 수령 필요 배지를 표시한다.

**색·컴포넌트**

- 상점 CTA는 `orange-500`.
- 품절은 회색, 잔액 부족은 `red-500` 배지.
- 캐릭터 상품은 도트 에셋 슬롯이 잘 보이도록 배경을 흰색으로 유지.

**필요 데이터**

- `walletBalance`
- `categories[]`
- `products[]`: `productId`, `name`, `thumbnailAsset`, `basePrice`, `taxAmount`, `finalPrice`, `stockStatus`, `isPhysical`, `purchaseLimit`, `canBuy`

**상태**

- `empty`: 판매 중인 상품 없음.
- `locked`: 상점 모듈 닫힘.
- `soldOut`: 카드 CTA 비활성.
- `insufficientBalance`: 상세는 가능, 구매 CTA 비활성.

**금지**

- 구매 불가 상품을 아무 설명 없이 비활성화하지 말 것.
- 재고와 잔액 부족 사유를 섞어서 표시하지 말 것.

### SCR-STU-007 상품 상세·구매

**목적**: 학생이 상품 가격, 세금, 구매 후 잔액을 확인하고 구매한다.

**레이아웃**

- 좌측 큰 상품 이미지 영역.
- 우측 구매 정보 패널: 상품명, 설명, 가격, 부가세, 최종 결제액, 구매 후 잔액.
- 하단에는 환불/수령 안내를 짧은 notice로 둔다.
- 구매 결과는 같은 화면에서 성공/실패 패널로 전환한다.

**색·컴포넌트**

- 최종 결제액은 `orange-500`, 구매 후 잔액은 `green-500` 또는 부족 시 `red-500`.
- 실제 상품 안내는 노란 notice, 디지털 상품은 민트 notice.

**필요 데이터**

- `productId`
- `name`
- `imageAsset`
- `description`
- `basePrice`
- `taxAmount`
- `finalPrice`
- `walletBalance`
- `walletAfterPurchase`
- `stockCount`
- `refundPolicyLabel`
- `purchaseRequestId`

**상태**

- `confirm`: 구매 전 확인.
- `processing`: 구매 처리 중.
- `success`: 구매번호, 지갑 변화, 다음 행동.
- `failed`: 잔액 부족, 재고 없음, 마감, 서버 실패 분리.

**금지**

- 처리 중에 성공처럼 잔액을 먼저 차감해 보이지 말 것.
- 실제 상품 수령 완료를 학생이 직접 처리하게 만들지 말 것.

### SCR-STU-008 보유 아이템·캐릭터

**목적**: 학생이 도트 캐릭터와 보유 아이템을 확인하고 장착한다.

**레이아웃**

- 좌측 큰 Character Slot. 실제 도트 PNG가 들어갈 빈 영역으로 둔다.
- 우측 아이템 목록. 탭은 추천, 모자, 얼굴, 상의, 하의, 신발, 배경.
- 하단 또는 좌측 아래에 현재 장착 아이템 요약.
- 미리보기 모드는 변경 전/후를 나란히 비교한다.

**색·컴포넌트**

- 캐릭터 슬롯 배경은 흰색 또는 아주 연한 하늘색.
- `image-rendering: pixelated` 전제.
- 착용 중은 민트 배지, 보유 중은 회색/초록 체크, 거래 잠금은 자물쇠 배지.

**필요 데이터**

- `characterAsset`
- `equippedItems[]`
- `ownedItems[]`: `itemId`, `category`, `name`, `asset`, `status`, `source`
- `previewItems[]`
- `canEquip`

**상태**

- `empty`: 보유 아이템 없음, 상점 이동 CTA.
- `preview`: 저장 전 미리보기.
- `saving`: 저장 처리 중.
- `lockedTrade`: 거래 중인 아이템 장착 불가.

**금지**

- UI가 캐릭터를 직접 생성하거나 대체 일러스트를 넣지 말 것.
- 도트 캐릭터 위에 흐림 효과, 강한 그림자, 과한 장식 오버레이 금지.

### SCR-STU-009 개인거래 목록

**목적**: 학생이 내가 보낸 제안, 받은 제안, 승인 대기, 완료 거래를 본다.

**레이아웃**

- 상단 제목 `개인거래`.
- 세그먼트 탭: 받은 제안, 보낸 제안, 승인 대기, 완료.
- 거래 목록은 세로 리스트. 각 행에 상대 닉네임, 금액/아이템, 상태, 남은 행동을 표시.
- 우측 또는 하단에 `새 거래 제안` CTA.

**색·컴포넌트**

- 대기 상태는 보라, 동의 필요는 주황, 완료는 초록, 거절은 빨강.
- 아이템이 포함된 거래는 작은 아이템 썸네일 표시.

**필요 데이터**

- `trades[]`: `tradeId`, `counterpartNickname`, `direction`, `amount`, `itemSummary`, `status`, `createdAt`, `actionRequired`
- `canCreateTrade`
- `blockedReason`

**상태**

- `empty`: 탭별 빈 상태.
- `locked`: 개인거래 모듈 닫힘.
- `hasAction`: 받은 제안에 동의/거절 CTA.

**금지**

- 상대 실명 노출 금지.
- 교사 승인 전 완료처럼 보이게 하지 말 것.

### SCR-STU-010 개인거래 작성

**목적**: 학생이 친구에게 돈 또는 아이템 거래를 제안한다.

**레이아웃**

- 단계형 폼: 친구 선택 -> 거래 내용 -> 세금·잠금 확인.
- 우측 요약 패널에 내 잔액 변화와 잠길 아이템을 표시.
- 하단 CTA `제안 보내기`.

**색·컴포넌트**

- 친구 선택은 검색 입력 + 학생 닉네임 리스트.
- 세금 예상은 연한 살구 notice, 아이템 잠금은 보라 notice.
- 최종 CTA는 `orange-500`.

**필요 데이터**

- `availableStudents[]`: `studentId`, `nickname`
- `walletBalance`
- `ownedTradableItems[]`
- `amount`
- `selectedItems[]`
- `estimatedTax`
- `walletAfter`
- `requestId`

**상태**

- `invalidAmount`: 0 이하 또는 잔액 부족.
- `itemLocked`: 이미 거래 중인 아이템.
- `processing`: 요청번호 생성 중.
- `success`: 받은 학생 동의 대기.

**금지**

- 실명 검색 금지.
- 세금 예상 없이 최종 제안 CTA 노출 금지.

### SCR-STU-011 개인거래 상세·동의

**목적**: 거래 당사자가 제안 내용을 확인하고 동의·거절·취소한다.

**레이아웃**

- 상단 상태 stepper: 제안됨 -> 친구 동의 -> 선생님 승인 -> 완료.
- 중앙 거래 요약 카드: 제안자, 상대, 금액, 아이템, 세금, 잔액 변화.
- 하단 CTA는 역할별로 다르게 표시: 동의/거절, 취소, 확인.

**색·컴포넌트**

- 현재 단계는 `purple-500`.
- 완료는 초록, 거절·취소는 빨강.
- 세금과 아이템 잠금 안내는 별도 notice.

**필요 데이터**

- `tradeId`
- `roleInTrade`
- `proposerNickname`
- `counterpartNickname`
- `amount`
- `items[]`
- `taxAmount`
- `status`
- `walletBefore`
- `walletAfter`
- `availableActions[]`

**상태**

- `AWAITING_COUNTERPART`: 상대 동의 CTA.
- `AWAITING_TEACHER`: 승인 대기 안내.
- `APPROVED`: 완료 결과.
- `REJECTED` 또는 `CANCELED`: 사유 표시.

**금지**

- 승인 대기 중 잔액이 이미 이동한 것처럼 표시 금지.
- 거래 당사자가 아닌 학생에게 상세 접근 허용 금지.

### SCR-STU-012 공동기금·투표

**목적**: 학생이 학급 공동기금 현황을 보고 익명 투표에 참여한다.

**레이아웃**

- 상단에는 공동기금 잔액과 사용 예정 요약.
- 투표가 LIVE면 중앙에 선택지 카드 2~4개를 크게 배치.
- 오른쪽에는 익명 안내와 제출 상태.
- 투표가 없으면 사용 내역 리스트를 보여준다.

**색·컴포넌트**

- 공동기금 잔액은 `green-500`.
- 투표 선택지는 크림 배경, 선택 시 `yellow-500` border.
- 익명 안내는 민트 notice.

**필요 데이터**

- `fundBalance`
- `fundHistory[]`
- `voteId`
- `voteTitle`
- `options[]`: `optionId`, `title`, `description`
- `hasSubmitted`
- `anonymousPolicyLabel`

**상태**

- `viewOnly`: 기금 조회만.
- `LIVE`: 투표 가능.
- `submitted`: 제출 완료, 변경 가능 여부 표시.
- `closed`: 결과 대기.

**금지**

- 개인 선택 결과를 노출하지 말 것.
- 공동기금과 개인 지갑 잔액을 같은 카드에 섞지 말 것.

### SCR-STU-013 금융 홈

**목적**: 금융 모듈을 한곳에서 탐색한다.

**레이아웃**

- 상단 제목 `이코 은행`.
- 2~4개 금융 카드: 예금, 적금, 투자, 대출/신용.
- 각 카드에는 상태, 예상 이자/시장 상태/신용 상태를 짧게 표시.
- 하단에 금융 용어 도움말 링크.

**색·컴포넌트**

- 은행 계열은 `purple-500`.
- 예금·적금은 민트/보라, 투자는 빨강/파랑, 대출·신용은 하늘색.

**필요 데이터**

- `enabledFinanceModules[]`
- `depositSummary`
- `savingSummary`
- `investmentSummary`
- `loanSummary`
- `creditSummary`

**상태**

- `empty`: 금융 모듈 없음. 홈으로 돌아가기.
- `locked`: 수업 후 열림.
- `active`: 카드 진입 가능.

**금지**

- 꺼진 금융 모듈을 카드로 보여주지 말 것.
- 실제 금융상품처럼 오해되는 표현 금지. 교육용 표현 유지.

### SCR-STU-014 정기예금

**목적**: 학생이 예금 상품 조건을 이해하고 가입 또는 상태를 확인한다.

**레이아웃**

- 좌측 상품 카드: 기간, 이자율, 최소 금액.
- 우측 가입 계산 패널: 원금 입력, 만기 예상액, 중도해지 안내.
- 가입 중인 계좌가 있으면 진행률 카드와 만기일을 우선 보여준다.

**색·컴포넌트**

- 예금 강조색 `purple-500`.
- 만기 예상액은 `green-500`.
- 중도해지 경고는 노란 notice.

**필요 데이터**

- `depositProducts[]`
- `activeDepositAccount`
- `principalAmount`
- `interestRate`
- `maturityDate`
- `expectedMaturityAmount`
- `earlyTerminationAmount`
- `walletBalance`

**상태**

- `available`: 가입 가능.
- `activeAccount`: 가입 상태 조회.
- `matured`: 만기 수령 가능.
- `earlyTerminate`: 중도해지 확인.

**금지**

- 이자 계산식만 던지고 결과를 숨기지 말 것.
- 원금보다 큰 금액 입력 시 가입 CTA 활성화 금지.

### SCR-STU-015 정기적금

**목적**: 학생이 적금 납입 일정과 납입 상태를 관리한다.

**레이아웃**

- 상단 적금 상태 카드: 목표, 누적 납입, 다음 납입일.
- 중앙 캘린더 또는 일정 리스트: `PAID`, `MISSED`, `DUE`.
- 오른쪽 재납입/해지 안내.

**색·컴포넌트**

- 납입 완료는 초록, 미납은 빨강, 예정은 보라.
- 일정 칩은 너무 둥글지 않게 radius 8~10.

**필요 데이터**

- `savingAccount`
- `installments[]`: `dueDate`, `amount`, `status`, `retryableUntil`
- `totalPaid`
- `expectedMaturityAmount`
- `walletBalance`

**상태**

- `available`: 가입 가능.
- `active`: 납입 일정 표시.
- `missedRetryable`: 유예 재납입 CTA.
- `terminated`: 해지 결과.

**금지**

- 미납을 실패처럼 과하게 꾸짖는 문구 금지.
- 재납입 가능 기간 없이 버튼만 노출 금지.

### SCR-STU-016 투자 시장

**목적**: 학생이 투자 가능한 가상 자산의 상태를 보고 주문 화면으로 이동한다.

**레이아웃**

- 상단에 시장 상태 `OPEN/CLOSED`와 보유 비빔.
- 자산 4종 카드 또는 표. 각 항목에 현재가, 전일 대비, 작은 선 그래프.
- 오른쪽에 교육용 리스크 안내.

**색·컴포넌트**

- 상승 초록, 하락 빨강, 보합 회색.
- 투자 CTA는 `red-500`보다 약간 부드러운 코랄 계열.
- 그래프는 과하게 화려한 차트보다 간단한 선 그래프.

**필요 데이터**

- `marketStatus`
- `walletBalance`
- `assets[]`: `assetId`, `name`, `iconAsset`, `currentPrice`, `changeAmount`, `changeRate`, `sparkline[]`
- `canOrder`

**상태**

- `OPEN`: 주문 가능.
- `CLOSED`: 조회만 가능.
- `emptyPrice`: 가격 데이터 없음, 재시도.

**금지**

- 실제 주식·코인 브랜드명 사용 금지.
- 투자 수익을 보장하는 문구 금지.

### SCR-STU-017 투자 주문

**목적**: 학생이 매수·매도 수량을 입력하고 예상 잔액을 확인한다.

**레이아웃**

- 좌측 선택 자산 정보.
- 중앙 주문 패널: 매수/매도 탭, 수량 stepper, 예상 금액.
- 우측 주문 후 잔액과 보유량 변화.
- 하단 확인 모달에서 최종 실행.

**색·컴포넌트**

- 매수는 초록, 매도는 보라 또는 파랑.
- 잔액 부족은 빨강 notice.
- 주문 확인 CTA는 시장이 열렸을 때만 활성.

**필요 데이터**

- `asset`
- `orderType`
- `quantity`
- `unitPrice`
- `estimatedTotal`
- `walletBalance`
- `holdingQuantity`
- `estimatedWalletAfter`
- `orderRequestId`

**상태**

- `invalidQuantity`: 정수 아님 또는 0 이하.
- `insufficientBalance`: 매수 불가.
- `insufficientHolding`: 매도 불가.
- `processing`: 주문 중.
- `success`/`failed`: 체결 결과.

**금지**

- 소수 수량 주문 허용 금지.
- 시장 CLOSED 상태에서 주문 CTA 노출 금지.

### SCR-STU-018 투자 포트폴리오

**목적**: 학생이 보유 투자 자산과 평가손익을 확인한다.

**레이아웃**

- 상단 Hero Card: 총 평가액, 총 손익.
- 중앙 보유 자산 리스트: 수량, 평균가, 현재가, 평가손익.
- 오른쪽 정산 요약 또는 오늘 변화 카드.

**색·컴포넌트**

- 이익 초록, 손실 빨강.
- 총 손익이 음수면 Hero Card를 연한 빨강 톤, 양수면 연한 초록 톤.

**필요 데이터**

- `portfolioValue`
- `totalProfitLoss`
- `holdings[]`: `assetId`, `name`, `quantity`, `averagePrice`, `currentPrice`, `profitLoss`
- `realizedProfitLoss`
- `taxAmount`

**상태**

- `empty`: 보유 자산 없음.
- `loadingPrice`: 가격 갱신 중.
- `readOnly`: 결과 조회만.

**금지**

- 공용 화면용 결과와 개인 포트폴리오를 섞지 말 것.
- 손익 색을 반대로 쓰지 말 것.

### SCR-STU-019 대출 신청·상환

**목적**: 학생이 교육용 대출을 신청하거나 상환 상태를 확인한다.

**레이아웃**

- 상단 신용 상태 notice.
- 신청 가능하면 상품 카드와 신청 금액 입력.
- 대출 중이면 상환 일정, 남은 원금, 다음 상환일을 우선 표시.
- 오른쪽에 유예·중도상환 안내.

**색·컴포넌트**

- 대출은 하늘색/보라 조합.
- 연체 또는 제한은 빨강 notice.
- 신청 CTA는 교사 승인 필요 배지와 함께 표시.

**필요 데이터**

- `creditStatus`
- `loanProducts[]`
- `activeLoan`
- `repaymentSchedule[]`
- `principalRemaining`
- `nextDueDate`
- `canApply`
- `approvalRequired`

**상태**

- `available`: 신청 가능.
- `APPLIED`: 교사 승인 대기.
- `ACTIVE`: 상환 중.
- `OVERDUE`: 연체 안내.
- `RESTRICTED`: 신청 불가.

**금지**

- 실제 금융권 대출처럼 보이는 법적 문구 사용 금지.
- 교사 승인 전 입금 완료처럼 표시 금지.

### SCR-STU-020 내 신용

**목적**: 학생이 교육용 신용 상태와 개선 방법을 이해한다.

**레이아웃**

- 중앙 큰 신용 상태 카드: `NORMAL`, `GOOD`, `CAUTION`, `RESTRICTED`.
- 아래에 영향을 준 최근 이벤트 3~5개.
- 오른쪽에 개선 팁.

**색·컴포넌트**

- GOOD 초록, NORMAL 하늘, CAUTION 노랑, RESTRICTED 빨강.
- 점수 숫자보다 등급과 설명 중심.

**필요 데이터**

- `creditStatus`
- `creditLabel`
- `creditEvents[]`: `title`, `impact`, `createdAt`
- `tips[]`
- `restrictedActions[]`

**상태**

- `noHistory`: 아직 신용 기록 없음.
- `restricted`: 제한된 행동 표시.

**금지**

- 실제 신용점수처럼 오해될 정밀 점수 노출 금지.
- 다른 학생과 비교 금지.

### SCR-STU-021 GAME 퀴즈

**목적**: 학생이 제한 시간 안에 GAME 퀴즈에 참여한다.

**레이아웃**

- 상단에 문제 번호, 남은 시간, 제출 상태.
- 중앙에 문제와 선택지 2~5개.
- 하단에 제출 CTA.
- 오른쪽에는 내 진행 상태만 표시. 전체 순위는 결과 전 노출 금지.

**색·컴포넌트**

- 타이머는 노랑/주황 강조.
- 선택지는 흰색 카드, 선택 시 `purple-500` border.
- 제출 완료는 초록.

**필요 데이터**

- `quizId`
- `questionId`
- `questionText`
- `choices[]`
- `timeRemaining`
- `selectedChoiceId`
- `submittedAt`
- `attemptStatus`

**상태**

- `OPEN`: 응답 가능.
- `submitted`: 변경 가능 여부 표시.
- `timeOver`: 제출 불가.
- `connectionLost`: 재연결 안내.

**금지**

- 정답 여부를 제출 즉시 표시하지 말 것.
- 다른 학생 답안 노출 금지.

### SCR-STU-022 TEST 응시

**목적**: 학생이 순위·보상 없는 TEST 문제에 응시한다.

**레이아웃**

- GAME과 유사하되 상단에 `테스트는 순위와 보상이 없어요` 안내.
- 문제 이동이 가능하면 좌측 또는 상단에 문제 번호 navigation.
- 제출 전 확인 모달.

**색·컴포넌트**

- TEST 안내는 하늘색 notice.
- 선택 상태는 보라 border.
- 최종 제출 CTA는 `orange-500`.

**필요 데이터**

- `testId`
- `questions[]`
- `currentQuestion`
- `answers[]`
- `remainingTime`
- `isFinalSubmit`

**상태**

- `inProgress`: 응시 중.
- `unanswered`: 미응답 문항 안내.
- `submitted`: 결과 대기.
- `closed`: 마감됨.

**금지**

- TEST 순위, 보상, 공개 랭킹 표시 금지.
- 응시 중 정답 노출 금지.

### SCR-STU-023 퀴즈 결과·오류 신고

**목적**: 학생이 개인 결과를 확인하고 문항 오류를 신고한다.

**레이아웃**

- 상단 결과 요약 카드: 맞힌 개수, 받은 보상, 공개 여부.
- 중앙 문항별 결과 리스트.
- 오른쪽 오류 신고 폼 또는 신고 CTA.
- GAME 공개 순위가 허용된 경우에만 작게 표시.

**색·컴포넌트**

- 정답 초록, 오답 빨강, 신고 가능 노랑.
- 보상은 `green-500`.

**필요 데이터**

- `quizResult`
- `score`
- `rewardAmount`
- `questionResults[]`
- `publicRank`
- `canReportQuestion`
- `reportReason`

**상태**

- `pendingScoring`: 채점 중.
- `published`: 결과 공개.
- `reported`: 신고 접수 완료.

**금지**

- TEST 결과를 공용 랭킹처럼 표현 금지.
- 다른 학생의 원점수·오답 노출 금지.

### SCR-STU-024 개인·최종 리포트

**목적**: 학생이 운영 중 또는 종료 후 본인 경제 활동 기록을 요약해 본다.

**레이아웃**

- 상단 큰 요약: 최종 잔액, 저축, 소비, 투자, 퀴즈 참여.
- 중앙은 섹션 카드: 지갑, 금융, 상점, 퀴즈.
- 오른쪽 또는 하단에 `조회 가능 기간` 안내.

**색·컴포넌트**

- 최종 잔액은 초록 Hero Card.
- 섹션 카드 border는 모듈별 색을 유지.
- 종료 후는 `readOnly` 배지와 차분한 톤.

**필요 데이터**

- `finalWalletBalance`
- `totalIncome`
- `totalExpense`
- `savingSummary`
- `investmentSummary`
- `quizSummary`
- `shopSummary`
- `readableUntil`

**상태**

- `active`: 운영 중 요약.
- `READ_ONLY`: 종료 후 30일 조회.
- `expired`: 조회 기간 종료 안내.

**금지**

- 반 평균과 비교해 학생을 평가하는 문구 금지.
- 교사용 리포트 세부 원본 노출 금지.

## 3. 교사 화면 생성 패턴

교사 상세 24개 화면은 `docs/ui/TEACHER_SCREENS_178_185.md`를 따른다. 생성기는 아래 패턴을 우선 적용한다.

### 3.1 개설·설정형 화면

적용 화면: 경제교실 기본 정보, 운영 기간, 모듈 선택, 정책 설정, 상품 등록.

| 항목 | 규칙 |
| --- | --- |
| 레이아웃 | 좌측 또는 상단 stepper, 중앙 폼, 우측 변경 요약 |
| 색 | 배경 `#F6FAFC`, 폼 패널 흰색, 주요 CTA 하늘색/민트 |
| 데이터 | 입력값, 검증 결과, 저장 가능 여부, 변경 요약 |
| 상태 | empty, dirty, validating, saving, saved, failed |
| 금지 | 저장 전 성공 상태 표시 금지, 정책 영향 요약 없이 저장 CTA 금지 |

### 3.2 목록·관리형 화면

적용 화면: 학생 목록, 상품 목록, 거래 승인 목록, 알림 목록.

| 항목 | 규칙 |
| --- | --- |
| 레이아웃 | 상단 필터/검색, 중앙 표, 우측 선택 상세 |
| 색 | 표 배경 흰색, 상태 칩은 낮은 채도 |
| 데이터 | rows, filters, selectedRow, pagination, bulkActions |
| 상태 | loading, empty, selected, bulkSelected, error |
| 금지 | 학생 식별 정보가 공용 화면 preview에 섞이지 않게 분리 |

### 3.3 돈 이동·승인형 화면

적용 화면: 급여 지급, 세금 정책, 개인거래 승인, 대출 승인, 실제 상품 수령.

| 항목 | 규칙 |
| --- | --- |
| 레이아웃 | 중앙 상세, 우측 최신 검증 결과, 하단 승인/반려 CTA |
| 색 | 승인 초록, 반려 빨강, 대기 보라 |
| 데이터 | requestId, actor, target, amount, latestValidation, policySnapshot |
| 상태 | pending, revalidating, approved, rejected, stale, failed |
| 금지 | 최신 잔액·정책 재검증 없이 승인 CTA 활성화 금지 |

## 4. 공용 화면 상세 스펙

### SCR-PUB-001 연결 대기

- 중앙에 연결 코드와 활동 대기 상태를 크게 보여준다.
- 배경은 학생 화면과 같은 파스텔 톤이지만 카드 수는 1개로 제한한다.
- 필요한 데이터: `displayCode`, `classroomName`, `connectionStatus`, `currentActivityTitle`.
- 금지: 학생 목록, 잔액, 접속자 실명 표시 금지.

### SCR-PUB-002 퀴즈 진행

- 상단에 활동명과 남은 시간, 중앙에 문제와 선택지, 하단에 익명 응답 수를 표시한다.
- 선택지별 집계는 교사가 공개 설정한 경우에만 표시한다.
- 필요한 데이터: `quizTitle`, `questionText`, `choices[]`, `timeRemaining`, `anonymousResponseCounts[]`.
- 금지: 개인 답안, 정답, 점수, 잔액 표시 금지.

### SCR-PUB-003 투표·이벤트 진행

- 중앙에 선택지와 이벤트 설명, 우측 또는 하단에 익명 참여 현황을 표시한다.
- 색은 `yellow-500`과 `mint-100`을 중심으로 밝게 유지한다.
- 필요한 데이터: `activityTitle`, `options[]`, `anonymousCounts[]`, `timeRemaining`, `phase`.
- 금지: 개인 선택과 금융 정보 표시 금지.

### SCR-PUB-004 결과·순위

- 공개 결과를 큰 숫자와 막대 그래프로 보여준다.
- 교사가 허용한 경우에만 1~3위 닉네임과 장착 캐릭터를 보여준다.
- 필요한 데이터: `resultTitle`, `summary`, `rankingPublic`, `topRankers[]`, `anonymousStats[]`.
- 금지: 실명, TEST 순위, 개인 세금·신용·잔액 표시 금지.

## 5. 생성 전 최종 체크

- 각 화면은 `screen_id`와 `role`에 따라 필요한 데이터만 받는다.
- 화면 DTO는 공통 메타와 화면별 `data`를 분리한다.
- `permissions.availableActions`에 없는 버튼은 숨기거나 비활성화한다.
- 학생 화면은 1280x800에서 핵심 CTA가 첫 화면에 보인다.
- 학생 홈은 3열 카드 그리드를 기준으로 하되, 선택되지 않은 모듈은 숨긴다.
- 카드 안에 카드가 중첩되지 않는다.
- 화면 제목과 핵심 금액을 카드 안에만 가두지 않는다.
- 모든 화면에 loading, empty, failed 또는 locked 중 최소 하나의 비정상 상태가 정의되어 있다.
- 돈 이동 화면은 처리 중과 실패 상태가 반드시 있다.
- 돈 이동 화면은 변경 전/후 금액과 요청번호 또는 거래번호를 표시한다.
- 도트 캐릭터 슬롯은 `image-rendering: pixelated` 적용을 전제로 크기와 여백을 잡는다.
- 공용 화면은 공개 스키마만 사용한다.
- 공용 화면은 3m 이상 거리에서 읽히도록 제목 44px 이상, 본문 30px 이상을 기준으로 한다.
- 생성 결과에서 특정 디자인 툴명은 사용하지 않는다.
- 생성 결과는 이 repo 안의 문서와 에셋만 근거로 삼는다.
