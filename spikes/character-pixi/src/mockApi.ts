import { CATALOG, CATALOG_BY_ID } from './catalog';
import { conflictsFor } from './assetProfile';
import type { CharacterDTO, CosmeticDTO, Slot, WalletDTO } from './types';

// ---------------------------------------------------------------------------
// Mock REST 계층. 실제 백엔드(Spring Boot + PostgreSQL 원장)의 엔드포인트 스키마만
// 흉내 낸다. 아래 각 함수의 TODO 위치에 실제 fetch 호출을 붙이면 된다.
// ---------------------------------------------------------------------------

const ROOM_ID = 'demo-room';
const STUDENT_ID = 'demo-student';
const LATENCY_MS = 120; // 네트워크 지연 흉내

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS));
}

export class ApiError extends Error {
  constructor(
    public code: 'ALREADY_OWNED' | 'INSUFFICIENT_FUNDS' | 'NOT_OWNED' | 'CLOSED',
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// --- 인메모리 원장(백엔드 대체) -------------------------------------------------
interface Store {
  character: CharacterDTO | null;
  wallet: WalletDTO;
  ownership: Set<string>; // cosmeticId 집합 (character_cosmetic_ownership)
  idempotency: Map<string, WalletDTO>; // Idempotency-Key → 최종 지갑 스냅샷 (replay)
}

const store: Store = {
  character: null,
  wallet: { balance: 250, spendTotal: 0 }, // 데모 시작 지갑(2일치 소득 수준)
  ownership: new Set(),
  idempotency: new Map(),
};

// GET /rooms/{roomId}/character  (지연 생성 — 첫 진입 시 무료 생성)
export async function getCharacter(): Promise<CharacterDTO> {
  // TODO(real): return fetch(`/api/rooms/${roomId}/character`).then(r => r.json())
  if (!store.character) {
    store.character = {
      id: 'char-1',
      roomId: ROOM_ID,
      studentId: STUDENT_ID,
      name: '이코',
      equipped: {},
      createdAt: new Date().toISOString(),
    };
  }
  return delay({ ...store.character, equipped: { ...store.character.equipped } });
}

// GET /rooms/{roomId}/cosmetics
export async function listCosmetics(): Promise<CosmeticDTO[]> {
  // TODO(real): return fetch(`/api/rooms/${roomId}/cosmetics`).then(r => r.json())
  return delay(CATALOG.slice());
}

// GET /rooms/{roomId}/wallet
export async function getWallet(): Promise<WalletDTO> {
  // TODO(real): return fetch(`/api/rooms/${roomId}/wallet`).then(r => r.json())
  return delay({ ...store.wallet });
}

// POST /rooms/{roomId}/character/cosmetics  { cosmeticId }  (헤더 Idempotency-Key)
// 백엔드: wallet row lock → 소유 검사 → 잔액 검증 → financial_transaction(COSMETIC_PURCHASE)
//         → 이중부기(지갑 -price, CHARACTER_EXPENSE +price) → ownership insert.
export async function purchase(
  cosmeticId: string,
  idempotencyKey: string,
): Promise<{ wallet: WalletDTO }> {
  // TODO(real): fetch POST with header 'Idempotency-Key': idempotencyKey
  const item = CATALOG_BY_ID[cosmeticId];
  if (!item) throw new ApiError('NOT_OWNED', '존재하지 않는 코스메틱');

  // Idempotency-Key replay: 같은 키 재요청은 원래 결과를 그대로 반환(D5 replay)
  const replayed = store.idempotency.get(idempotencyKey);
  if (replayed) return delay({ wallet: { ...replayed } });

  // 이미 소유(다른 키) → 409 ALREADY_OWNED (D5)
  if (store.ownership.has(cosmeticId)) {
    throw new ApiError('ALREADY_OWNED', '이미 보유한 코스메틱입니다');
  }
  // 잔액 검증 — 소비는 한 방향(환불 없음)
  if (store.wallet.balance < item.price) {
    throw new ApiError('INSUFFICIENT_FUNDS', '비빔이 부족합니다');
  }

  store.wallet.balance -= item.price;
  store.wallet.spendTotal += item.price;
  store.ownership.add(cosmeticId);
  const snapshot: WalletDTO = { ...store.wallet };
  store.idempotency.set(idempotencyKey, snapshot);
  return delay({ wallet: snapshot });
}

// GET /rooms/{roomId}/character/ownership
export async function listOwnership(): Promise<string[]> {
  // TODO(real): return fetch(...).then(r => r.json())
  return delay([...store.ownership]);
}

// PATCH /rooms/{roomId}/character/equipped  { slot, cosmeticId|null }  (돈 이동 없음)
export async function equip(
  slot: Slot,
  cosmeticId: string | null,
): Promise<CharacterDTO> {
  // TODO(real): fetch PATCH — equipped JSONB만 갱신, 원장 미기록(D4)
  const char = await getCharacter();
  if (cosmeticId !== null) {
    if (!store.ownership.has(cosmeticId)) {
      throw new ApiError('NOT_OWNED', '보유하지 않은 코스메틱은 착용할 수 없습니다');
    }
    const item = CATALOG_BY_ID[cosmeticId];
    if (!item || item.slot !== slot) {
      throw new ApiError('NOT_OWNED', '슬롯이 맞지 않습니다');
    }
    const equipped = {
      BACKGROUND: store.character!.equipped.BACKGROUND ?? null,
      EFFECT: store.character!.equipped.EFFECT ?? null,
      BACK: store.character!.equipped.BACK ?? null,
      BOTTOM: store.character!.equipped.BOTTOM ?? null,
      SHOES: store.character!.equipped.SHOES ?? null,
      TOP: store.character!.equipped.TOP ?? null,
      NECK: store.character!.equipped.NECK ?? null,
      FACE: store.character!.equipped.FACE ?? null,
      HAND: store.character!.equipped.HAND ?? null,
      HAT: store.character!.equipped.HAT ?? null,
    };
    for (const conflictingSlot of conflictsFor(item, equipped)) {
      delete store.character!.equipped[conflictingSlot];
    }
    store.character!.equipped[slot] = cosmeticId;
  } else {
    delete store.character!.equipped[slot];
  }
  return delay({ ...store.character!, equipped: { ...store.character!.equipped } });
}
