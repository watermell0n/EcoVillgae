export type Slot =
  | 'BACKGROUND'
  | 'EFFECT'
  | 'BACK'
  | 'BOTTOM'
  | 'SHOES'
  | 'TOP'
  | 'NECK'
  | 'FACE'
  | 'HAND'
  | 'HAT';

// 상점·백엔드가 공유하는 슬롯 순서다. 실제 렌더 레이어 순서는 layerOrder.ts가 관리한다.
export const SLOT_Z_ORDER: Slot[] = [
  'BACKGROUND',
  'EFFECT',
  'BACK',
  'BOTTOM',
  'SHOES',
  'TOP',
  'NECK',
  'FACE',
  'HAND',
  'HAT',
];

export const SLOT_LABEL: Record<Slot, string> = {
  BACKGROUND: '배경',
  EFFECT: '이펙트',
  BACK: '등·꼬리',
  BOTTOM: '하의',
  SHOES: '신발',
  TOP: '몸통',
  NECK: '목 장식',
  FACE: '얼굴',
  HAND: '손 소품',
  HAT: '머리',
};

export type Theme = 'BASIC' | 'SEASONAL' | 'SPORTS' | 'CAREER' | 'FANTASY' | 'EVENT';

export const THEME_LABEL: Record<Theme, string> = {
  BASIC: '기본',
  SEASONAL: '계절',
  SPORTS: '스포츠',
  CAREER: '직업',
  FANTASY: '판타지',
  EVENT: '행사',
};

export interface CosmeticDTO {
  id: string;
  roomId: string;
  name: string;
  slot: Slot;
  price: number;
  spriteKey: string;
  theme: Theme;
  displayOrder: number;
  status: 'ACTIVE' | 'HIDDEN';
}

export interface CharacterDTO {
  id: string;
  roomId: string;
  studentId: string;
  name: string;
  equipped: Partial<Record<Slot, string>>;
  createdAt: string;
}

export interface WalletDTO {
  balance: number;
  spendTotal: number;
}

export type EquippedMap = Record<Slot, string | null>;

export const EMPTY_EQUIPPED: EquippedMap = {
  BACKGROUND: null,
  EFFECT: null,
  BACK: null,
  BOTTOM: null,
  SHOES: null,
  TOP: null,
  NECK: null,
  FACE: null,
  HAND: null,
  HAT: null,
};
