import type { ModuleCard, StudentScreenEnvelope } from "../types/screen";

export const moduleCards: ModuleCard[] = [
  {
    id: "shop",
    title: "이코 상점",
    subtitle: "상품을 사고 지갑 변화를 확인해요",
    icon: "🏪",
    tone: "orange",
    state: "active",
    cta: "입장하기"
  },
  {
    id: "bank",
    title: "이코 은행",
    subtitle: "예금과 적금을 살펴봐요",
    icon: "🏦",
    tone: "purple",
    state: "active",
    cta: "입장하기"
  },
  {
    id: "investment",
    title: "투자 광장",
    subtitle: "시장 가격과 내 손익을 확인해요",
    icon: "📈",
    tone: "red",
    state: "resultOnly",
    cta: "결과 보기"
  },
  {
    id: "paystub",
    title: "월급명세서",
    subtitle: "받은 돈과 세금을 확인해요",
    icon: "💼",
    tone: "green",
    state: "active",
    cta: "확인하기"
  },
  {
    id: "meeting",
    title: "마을 회의",
    subtitle: "공동기금 사용처에 투표해요",
    icon: "🗳️",
    tone: "yellow",
    state: "locked",
    cta: "수업 후 열려요"
  },
  {
    id: "character",
    title: "내 캐릭터",
    subtitle: "보유 아이템을 장착해요",
    icon: "🙂",
    tone: "sky",
    state: "new",
    cta: "꾸미기"
  }
];

export const studentHomeEnvelope: StudentScreenEnvelope<{ cards: ModuleCard[]; announcement: string }> = {
  screenId: "SCR-STU-003",
  role: "STU",
  mode: "AUTONOMOUS",
  classroom: {
    id: "class-1",
    name: "이코빌리지",
    dayIndex: 1,
    status: "ACTIVE",
    currencyName: "비빔"
  },
  studentView: {
    nickname: "학생",
    walletBalance: 158
  },
  permissions: {
    canView: true,
    availableActions: ["OPEN_SHOP", "VIEW_WALLET", "VIEW_PAYSTUB", "EQUIP_ITEM"],
    blockedReason: null
  },
  uiState: "active",
  lastSyncedAt: "2026-07-30T09:00:00+09:00",
  data: {
    cards: moduleCards,
    announcement: "자율시간이에요. 열린 마을 기능만 사용할 수 있어요."
  }
};

export const paystub = {
  grossSalary: 150,
  bonusAmount: 25,
  incomeTax: 17,
  deductionAmount: 0,
  netSalary: 158,
  walletBefore: 0,
  walletAfter: 158,
  taxDestinationLabel: "우리 반 공동기금",
  confirmedAt: null
};

export const transactions = [
  { id: "TXN-1001", type: "income", title: "1인 1역 월급", amount: 150, after: 150 },
  { id: "TXN-1002", type: "income", title: "퀴즈 정답수당", amount: 25, after: 175 },
  { id: "TXN-1003", type: "tax", title: "소득세", amount: -17, after: 158 }
];
