import { apiRequest } from "../../api/client";
import { CATALOG, CATALOG_BY_ID } from "./catalog";
import { conflictsFor } from "./assetProfile";
import type { CosmeticDTO, EquippedMap, Slot } from "./types";
import { EMPTY_EQUIPPED } from "./types";

export type CharacterStoreItem = CosmeticDTO & {
  productId: string;
  inventoryItemId: string | null;
  owned: boolean;
  equipped: boolean;
  stock: number | null;
  productVersion: number;
  inventoryStateVersion: number | null;
};

type StoreCatalogResponse = {
  products: Array<{
    resource_id: string;
    label: string;
    status: string;
    version: number;
    state_version: number;
    unit_price: number;
    product_kind: string;
    character_code?: string;
    available_quantity?: number;
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

const USE_MOCK = import.meta.env.VITE_USE_MOCK_STUDENT_FEATURES !== "false";

const mockOwned = new Set([
  "hat_red_cap",
  "top_blue_tee",
  "bottom_striped_pants",
  "shoes_sneakers"
]);

const mockEquipped: EquippedMap = {
  ...EMPTY_EQUIPPED,
  HAT: "hat_red_cap",
  TOP: "top_blue_tee",
  BOTTOM: "bottom_striped_pants",
  SHOES: "shoes_sneakers"
};

function mockItems(): CharacterStoreItem[] {
  return CATALOG.map((item) => ({
    ...item,
    productId: item.id,
    inventoryItemId: mockOwned.has(item.id) ? `mock-inventory-${item.id}` : null,
    owned: mockOwned.has(item.id),
    equipped: mockEquipped[item.slot] === item.id,
    stock: 99,
    productVersion: 1,
    inventoryStateVersion: mockOwned.has(item.id) ? 0 : null
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
    const cosmetic = product.character_code
      ? CATALOG_BY_ID[product.character_code]
      : CATALOG.find((item) => item.name === product.label);
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
      stock: product.available_quantity ?? null,
      productVersion: product.version,
      inventoryStateVersion: owned?.state_version ?? null
    }];
  });
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
      stock: null,
      productVersion: owned.version,
      inventoryStateVersion: owned.state_version
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

export function equippedMap(items: CharacterStoreItem[]): EquippedMap {
  const equipped = { ...EMPTY_EQUIPPED };
  for (const item of items) {
    if (item.equipped) {
      equipped[item.slot as Slot] = item.id;
    }
  }
  return equipped;
}
