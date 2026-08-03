export type ModuleCard = {
  id: "shop" | "bank" | "investment" | "paystub" | "meeting" | "character";
  title: string;
  subtitle: string;
  icon: string;
  tone: "orange" | "green" | "purple" | "red" | "yellow" | "sky";
  state: "active" | "locked" | "new" | "resultOnly";
  cta: string;
};

export const moduleCards: ModuleCard[] = [
  { id: "shop", title: "이코 상점", subtitle: "상품을 사고 지갑 변화를 확인해요", icon: "🏪", tone: "orange", state: "active", cta: "입장하기" },
  { id: "bank", title: "이코 은행", subtitle: "예금과 적금을 살펴봐요", icon: "🏦", tone: "purple", state: "active", cta: "준비 중" },
  { id: "investment", title: "투자 광장", subtitle: "시장 가격과 내 손익을 확인해요", icon: "📈", tone: "red", state: "resultOnly", cta: "준비 중" },
  { id: "paystub", title: "월급명세서", subtitle: "받은 돈과 세금을 확인해요", icon: "💼", tone: "green", state: "active", cta: "준비 중" },
  { id: "meeting", title: "마을 회의", subtitle: "공동기금 사용처에 투표해요", icon: "🗳️", tone: "yellow", state: "locked", cta: "수업 후 열려요" },
  { id: "character", title: "내 캐릭터", subtitle: "보유 아이템을 장착해요", icon: "🙂", tone: "sky", state: "new", cta: "이전 예정" }
];
export const transactions = [
  { id: "TXN-1001", title: "1인 1역 월급", amount: 150, after: 150 },
  { id: "TXN-1002", title: "퀴즈 정답수당", amount: 25, after: 175 },
  { id: "TXN-1003", title: "소득세", amount: -17, after: 158 }
];
