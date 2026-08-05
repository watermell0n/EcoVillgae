import { apiRequest } from "../../api/client";
import {
  CATALOG,
  CATALOG_BY_ID,
  EMPTY_EQUIPPED,
  OFF_SALE_SPRITE_KEYS,
  conflictsFor,
  type CosmeticDTO,
  type EquippedMap,
  type Slot
} from "@character-core";

export type CharacterStoreItem = CosmeticDTO & {
  productId: string;
  inventoryItemId: string | null;
  owned: boolean;
  equipped: boolean;
  vatRateBps: number;
  productVersion: number;
  inventoryStateVersion: number | null;
  /** 상점 상세 카드 문구. 백엔드 store_products.description이 정본이다. */
  description: string | null;
  /** 상점 기본 노출 순서. 백엔드 classroom_characters.sort_order가 정본이다. */
  sortOrder: number | null;
  /**
   * 서버 상품 상태(`PUBLISHED` 등).
   *
   * `CosmeticDTO.status`는 로컬 카탈로그 값이라 언제나 `ACTIVE`다. 그것으로 노출을 거르면
   * 아무것도 걸러지지 않으면서 걸러진다고 착각하게 된다. 서버 값을 따로 담는다.
   */
  productStatus: string;
};

/**
 * 학생에게 보이면 안 되는 상품 상태.
 *
 * 허용 목록이 아니라 차단 목록이다. 서버가 새 상태를 추가했을 때 허용 목록이면 상품이
 * 조용히 사라지는데, 그게 이번에 고친 "10종만 보이던" 문제와 같은 종류의 사고다.
 * 학생 카탈로그 조회는 서버가 이미 게시·구매 가능 상품만 주므로 여기는 이중 안전장치다.
 */
const HIDDEN_PRODUCT_STATUSES = new Set(["DRAFT", "UNPUBLISHED", "ARCHIVED", "RETIRED"]);

export function isPurchasableStatus(item: CharacterStoreItem): boolean {
  return !HIDDEN_PRODUCT_STATUSES.has(item.productStatus.toUpperCase());
}

/**
 * 상점 기본 노출 순서. 백엔드 sortOrder를 1순위로 쓰고, 값이 없으면 카탈로그 순서와
 * 이름으로 안정 정렬한다. 정렬이 흔들리면 페이지네이션에서 상품이 튄다.
 */
export function compareStoreItems(a: CharacterStoreItem, b: CharacterStoreItem): number {
  const order = (item: CharacterStoreItem) => item.sortOrder ?? Number.MAX_SAFE_INTEGER;
  if (order(a) !== order(b)) return order(a) - order(b);
  if (a.displayOrder !== b.displayOrder) return a.displayOrder - b.displayOrder;
  return a.name.localeCompare(b.name, "ko");
}

type StoreCatalogResponse = {
  products: Array<{
    resource_id: string;
    label: string;
    status: string;
    version: number;
    state_version: number;
    unit_price: number;
    vat_rate_bps?: number;
    product_kind: string;
    character_code?: string;
    description?: string | null;
    sort_order?: number | null;
  }>;
};

type InventoryResponse = {
  inventory_items: Array<{
    resource_id: string;
    label: string;
    status: string;
    version: number;
    state_version: number;
    character_code: string;
    equipped: boolean;
  }>;
};

export type CharacterProfile = {
  profile_id: string;
  classroom_student_id: string;
  state_version: number;
  equipment_changed_at: string | null;
  created_at: string;
  updated_at: string;
  equipped_inventory_items: Partial<Record<Slot, string>>;
};

const USE_MOCK = import.meta.env.VITE_USE_MOCK_STUDENT_FEATURES === "true";

/**
 * 모의 보유 목록.
 *
 * `hat_straw`·`top_swimsuit`·`shoes_sandals`는 판매 종료 상품인데 일부러 남긴다.
 * "판매는 끝났지만 이미 산 상품"이 상점 미리보기·착용 스트립·일괄 저장에서 제대로
 * 다뤄지는지 개발 중에 눈으로 확인할 수 있어야 한다.
 */
const mockOwned = new Set([
  "hat_red_cap",
  "top_blue_tshirt",
  "shoes_sneakers",
  "back_backpack",
  "hat_straw",
  "top_swimsuit",
  "shoes_sandals",
  "bg_spring"
]);

const mockEquipped: EquippedMap = {
  ...EMPTY_EQUIPPED
};

const mockProfile: CharacterProfile = {
  profile_id: "mock-character-profile",
  classroom_student_id: "mock-classroom-student",
  state_version: 0,
  equipment_changed_at: null,
  created_at: new Date(0).toISOString(),
  updated_at: new Date(0).toISOString(),
  equipped_inventory_items: {}
};

function mockItems(): CharacterStoreItem[] {
  return CATALOG.map((item) => ({
    ...item,
    productId: item.id,
    inventoryItemId: mockOwned.has(item.id) ? `mock-inventory-${item.id}` : null,
    owned: mockOwned.has(item.id),
    equipped: mockEquipped[item.slot] === item.id,
    vatRateBps: 0,
    productVersion: 1,
    inventoryStateVersion: mockOwned.has(item.id) ? 0 : null,
    description: `${item.name}을(를) 캐릭터에 착용해 볼 수 있어요.`,
    sortOrder: item.displayOrder,
    // 서버 상태를 흉내낸다. 전부 PUBLISHED로 두면 개발 중 상점과 실제 상점이 달라진다.
    productStatus: OFF_SALE_SPRITE_KEYS.has(item.spriteKey) ? 'UNPUBLISHED' : 'PUBLISHED'
  }));
}

export async function fetchCharacterStore(classroomId: string): Promise<CharacterStoreItem[]> {
  if (USE_MOCK) {
    return mockItems();
  }

  const [store, inventory] = await Promise.all([
    apiRequest<StoreCatalogResponse>(`/classrooms/${classroomId}/store/catalog`),
    apiRequest<InventoryResponse>(`/classrooms/${classroomId}/students/me/inventory`)
  ]);

  const inventoryByCode = new Map(
    inventory.inventory_items.map((item) => [item.character_code, item])
  );

  return store.products.flatMap((product) => {
    if (product.product_kind !== "CHARACTER") {
      return [];
    }
    if (!product.character_code) {
      return [];
    }
    const cosmetic = CATALOG_BY_ID[product.character_code];
    if (!cosmetic) {
      return [];
    }
    const owned = inventoryByCode.get(cosmetic.spriteKey);
    return [{
      ...cosmetic,
      price: product.unit_price,
      productId: product.resource_id,
      inventoryItemId: owned?.resource_id ?? null,
      owned: Boolean(owned),
      equipped: owned?.equipped ?? false,
      vatRateBps: product.vat_rate_bps ?? 0,
      productVersion: product.version,
      inventoryStateVersion: owned?.state_version ?? null,
      description: product.description ?? null,
      sortOrder: product.sort_order ?? null,
      productStatus: product.status
    }];
  });
}

export async function fetchCharacterProfile(classroomId: string): Promise<CharacterProfile> {
  if (USE_MOCK) return mockProfile;
  return apiRequest<CharacterProfile>(
    `/classrooms/${classroomId}/students/me/character-profile`
  );
}

export async function fetchOwnedCharacters(classroomId: string): Promise<CharacterStoreItem[]> {
  if (USE_MOCK) {
    return mockItems().filter((item) => item.owned);
  }

  const inventory = await apiRequest<InventoryResponse>(
    `/classrooms/${classroomId}/students/me/inventory`
  );
  return inventory.inventory_items.flatMap((owned) => {
    const cosmetic = CATALOG_BY_ID[owned.character_code];
    if (!cosmetic) {
      return [];
    }
    return [{
      ...cosmetic,
      productId: "",
      inventoryItemId: owned.resource_id,
      owned: true,
      equipped: owned.equipped,
      vatRateBps: 0,
      productVersion: owned.version,
      inventoryStateVersion: owned.state_version,
      // 보관함 응답에는 상점 상품 필드가 없다. 상세 문구는 상점 카탈로그에서만 온다.
      description: null,
      sortOrder: null,
      productStatus: 'PUBLISHED'
    }];
  });
}

export function acquireMockCharacter(cosmeticId: string) {
  if (USE_MOCK) {
    mockOwned.add(cosmeticId);
  }
}

export async function setCharacterEquipped(
  classroomId: string,
  item: CharacterStoreItem,
  equipped: boolean
) {
  if (USE_MOCK) {
    if (!mockOwned.has(item.id)) {
      throw new Error("보유하지 않은 아이템입니다.");
    }
    if (equipped) {
      for (const slot of conflictsFor(item, mockEquipped)) {
        mockEquipped[slot] = null;
      }
      mockEquipped[item.slot] = item.id;
    } else if (mockEquipped[item.slot] === item.id) {
      mockEquipped[item.slot] = null;
    }
    return;
  }

  if (!item.inventoryItemId) {
    throw new Error("장착할 인벤토리 아이템을 찾을 수 없습니다.");
  }
  await apiRequest(
    `/classrooms/${classroomId}/inventory-items/${item.inventoryItemId}/commands`,
    {
      method: "POST",
      body: JSON.stringify({
        command: equipped ? "EQUIP" : "UNEQUIP",
        expected_state_version: item.inventoryStateVersion,
        screen_id: "SCR-STU-008",
        ui_action_id: "UIA-ITM-001",
        client_occurred_at: new Date().toISOString()
      })
    }
  );
}

/**
 * 착용 상태를 한 번에 저장한다(API-ITM-002). 상점의 '저장된 모습 유지'가 쓴다.
 *
 * 슬롯별 단건 명령을 반복하면 6슬롯 저장에 최대 6번의 요청이 나가고, 중간에 실패하면
 * 캐릭터가 일부 슬롯만 저장된 상태로 남는다. 서버가 한 트랜잭션에서 확정하게 맡긴다.
 *
 * @param equipment 슬롯 → cosmeticId. 서버에는 인벤토리 아이템 id로 바꿔 보낸다.
 */
export async function saveCharacterEquipment(
  classroomId: string,
  expectedStateVersion: number,
  equipment: EquippedMap,
  items: CharacterStoreItem[]
): Promise<CharacterProfile> {
  const inventoryIdByCosmetic = new Map(
    items
      .filter((item) => item.inventoryItemId)
      .map((item) => [item.id, item.inventoryItemId as string])
  );

  const payload: Partial<Record<Slot, string | null>> = {};
  for (const [slot, cosmeticId] of Object.entries(equipment) as Array<[Slot, string | null]>) {
    if (!cosmeticId) {
      payload[slot] = null;
      continue;
    }
    const inventoryItemId = inventoryIdByCosmetic.get(cosmeticId);
    if (!inventoryItemId) {
      throw new Error("보유하지 않은 아이템은 저장할 수 없어요.");
    }
    payload[slot] = inventoryItemId;
  }

  if (USE_MOCK) {
    for (const [slot, cosmeticId] of Object.entries(equipment) as Array<[Slot, string | null]>) {
      mockEquipped[slot] = cosmeticId;
    }
    mockProfile.state_version += 1;
    mockProfile.equipped_inventory_items = Object.fromEntries(
      Object.entries(payload).filter(([, value]) => value !== null) as Array<[Slot, string]>
    );
    return mockProfile;
  }

  return apiRequest<CharacterProfile>(
    `/classrooms/${classroomId}/students/me/character-profile/equipment`,
    {
      method: "PUT",
      body: JSON.stringify({
        expected_state_version: expectedStateVersion,
        equipment: payload,
        screen_id: "SCR-STU-007",
        ui_action_id: "UIA-ITM-002",
        client_occurred_at: new Date().toISOString()
      })
    }
  );
}

export function equippedMap(items: CharacterStoreItem[]): EquippedMap {
  const equipped = { ...EMPTY_EQUIPPED };
  for (const item of items) {
    if (item.equipped) {
      equipped[item.slot as Slot] = item.id;
    }
  }
  return equipped;
}
