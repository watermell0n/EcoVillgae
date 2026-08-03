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
  { resourceId: "mock-cap", label: "탐험가 모자", status: "PUBLISHED", version: 1, stateVersion: 1, unitPrice: 50, productKind: "DIGITAL", characterCode: "HEAD_EXPLORER_CAP" },
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
