import { apiRequest } from "@/api/client";

export type StoreProduct = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  stateVersion: number;
  unitPrice: number;
  productKind: string;
  characterCode: string | null;
};

type StoreProductResponse = {
  resource_id: string;
  label: string;
  status: string;
  version: number;
  state_version: number;
  unit_price: number;
  product_kind: string;
  character_code: string | null;
};

type StoreCatalogResponse = { products: StoreProductResponse[] };

const mockProducts: StoreProduct[] = [
  { resourceId: "mock-pencil", label: "행운 연필", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 35, productKind: "PHYSICAL", characterCode: null },
  { resourceId: "mock-cap", label: "빨간 캡", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 30, productKind: "DIGITAL", characterCode: "hat_red_cap" },
  { resourceId: "mock-beanie", label: "민트 비니", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 30, productKind: "DIGITAL", characterCode: "hat_teal_beanie" },
  { resourceId: "mock-hoodie", label: "노란 후드", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 45, productKind: "DIGITAL", characterCode: "top_yellow_hoodie" },
  { resourceId: "mock-shirt", label: "파란 티셔츠", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 35, productKind: "DIGITAL", characterCode: "top_blue_tshirt" },
  { resourceId: "mock-overalls", label: "멜빵 바지", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 45, productKind: "DIGITAL", characterCode: "bottom_overalls" },
  { resourceId: "mock-sneakers", label: "운동화", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 30, productKind: "DIGITAL", characterCode: "shoes_sneakers" },
  { resourceId: "mock-backpack", label: "노란 배낭", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 40, productKind: "DIGITAL", characterCode: "back_backpack" },
  { resourceId: "mock-sunglasses", label: "선글라스", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 25, productKind: "DIGITAL", characterCode: "face_sunglasses" },
  { resourceId: "mock-seed", label: "마을 씨앗", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 25, productKind: "PHYSICAL", characterCode: null }
];

export async function fetchStoreCatalog(classroomId: string) {
  if (process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true") {
    await new Promise((resolve) => setTimeout(resolve, 250));
    return mockProducts;
  }

  const response = await apiRequest<StoreCatalogResponse>(`/classrooms/${classroomId}/store/catalog`);
  return response.products.map((product) => ({
    resourceId: product.resource_id,
    label: product.label,
    status: product.status,
    version: product.version,
    stateVersion: product.state_version,
    unitPrice: product.unit_price,
    productKind: product.product_kind,
    characterCode: product.character_code
  }));
}

export async function purchaseStoreProduct(classroomId: string, product: StoreProduct) {
  if (process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true") {
    return { orderId: `mock-order-${product.resourceId}`, status: "PAID", totalAmount: product.unitPrice, stateVersion: 1 };
  }

  const response = await apiRequest<{ order_id: string; status: string; total_amount: number; state_version: number }>(`/classrooms/${classroomId}/store/orders`, {
    method: "POST",
    retryOnNetworkFailure: true,
    // expected_state_version is omitted on purpose: the server compares it with the
    // classroom state version, which the student app never receives. Sending a literal
    // value rejects every purchase in a classroom that has been edited even once.
    body: JSON.stringify({
      screen_id: "SCR-STU-006",
      ui_action_id: "UIA-SHP-002",
      client_occurred_at: new Date().toISOString(),
      product_id: product.resourceId,
      quantity: 1,
      expected_product_version: product.version
    })
  });
  return { orderId: response.order_id, status: response.status, totalAmount: response.total_amount, stateVersion: response.state_version };
}
