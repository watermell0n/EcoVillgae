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

// 기존 상점/백엔드 슬롯의 호환 순서다. 실제 부착점과 레이어는 character-rig.json에서 관리한다.
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

export type BodyState = 'CHUBBY' | 'BASE' | 'SLIM' | 'VERY_SLIM';

// 학생에게 노출되는 명칭은 체중을 직접 연상시키지 않는 중립적인 표현을 사용한다.
export const BODY_LABEL: Record<BodyState, string> = {
  CHUBBY: '포근',
  BASE: '기본',
  SLIM: '산뜻',
  VERY_SLIM: '가벼움',
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
