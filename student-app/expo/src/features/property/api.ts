import { ApiError, apiRequest } from "@/api/client";

const useMocks = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true";

export type ContractType = "RENT" | "JEONSE" | "SALE";

export type Property = {
  propertyId: string;
  coordinate: string;
  displayName: string;
  tier: string;
  allowsRent: boolean;
  allowsJeonse: boolean;
  allowsSale: boolean;
  allowsSubscription: boolean;
  salePrice: number;
  depositAmount: number;
  monthlyRent: number;
  paymentCycleDays: number;
  contractTermDays: number;
  ownerKind: string;
  ownerStudentId?: string;
  status: string;
  subscriptionOfferId?: string;
};

export type PropertyContract = {
  contractId: string;
  propertyId: string;
  contractType: string;
  depositAmount: number;
  monthlyRent: number;
  salePrice: number;
  startedAt?: string;
  endsAt?: string;
  nextPaymentAt?: string;
  status: string;
};

export type SubscriptionAccount = {
  totalDepositAmount: number;
  depositCount: number;
  lastDepositedAt?: string;
  autoDepositAmount?: number;
  autoDepositCycleDays?: number;
  nextAutoDepositAt?: string;
  status: string;
  stateVersion: number;
};

export type SubscriptionApplication = {
  applicationId: string;
  offerId: string;
  depositCount: number;
  depositAmount: number;
  assignedPropertyId?: string;
  status: string;
  appliedAt?: string;
};

type PropertyResponse = {
  property_id?: string;
  coordinate?: string;
  display_name?: string;
  tier?: string;
  allows_rent?: boolean;
  allows_jeonse?: boolean;
  allows_sale?: boolean;
  allows_subscription?: boolean;
  sale_price?: number;
  deposit_amount?: number;
  monthly_rent?: number;
  payment_cycle_days?: number;
  contract_term_days?: number;
  owner_kind?: string;
  owner_student_id?: string | null;
  status?: string;
  subscription_offer_id?: string | null;
};

type ContractResponse = {
  contract_id?: string;
  property_id?: string;
  contract_type?: string;
  deposit_amount?: number;
  monthly_rent?: number;
  sale_price?: number;
  started_at?: string | null;
  ends_at?: string | null;
  next_payment_at?: string | null;
  status?: string;
};

type SubscriptionAccountResponse = {
  total_deposit_amount?: number;
  deposit_count?: number;
  last_deposited_at?: string | null;
  auto_deposit_amount?: number | null;
  auto_deposit_cycle_days?: number | null;
  next_auto_deposit_at?: string | null;
  status?: string;
  state_version?: number;
};

type SubscriptionApplicationResponse = {
  application_id?: string;
  offer_id?: string;
  deposit_count?: number;
  deposit_amount?: number;
  assigned_property_id?: string | null;
  status?: string;
  applied_at?: string | null;
};

const mockProperties: Property[] = [
  {
    propertyId: "mock-property-a1", coordinate: "A1", displayName: "창가 1번 자리", tier: "STANDARD",
    allowsRent: true, allowsJeonse: true, allowsSale: true, allowsSubscription: false,
    salePrice: 320, depositAmount: 120, monthlyRent: 24, paymentCycleDays: 7, contractTermDays: 30,
    ownerKind: "CLASSROOM", status: "AVAILABLE"
  },
  {
    propertyId: "mock-property-b2", coordinate: "B2", displayName: "칠판 앞 자리", tier: "PREMIUM",
    allowsRent: true, allowsJeonse: false, allowsSale: false, allowsSubscription: true,
    salePrice: 0, depositAmount: 60, monthlyRent: 18, paymentCycleDays: 7, contractTermDays: 30,
    ownerKind: "CLASSROOM", status: "LISTED_FOR_RENT"
  }
];

function mapProperty(item: PropertyResponse): Property[] {
  if (!item.property_id) return [];
  return [{
    propertyId: item.property_id,
    coordinate: item.coordinate ?? "",
    displayName: item.display_name ?? item.coordinate ?? "자리",
    tier: item.tier ?? "STANDARD",
    allowsRent: item.allows_rent ?? false,
    allowsJeonse: item.allows_jeonse ?? false,
    allowsSale: item.allows_sale ?? false,
    allowsSubscription: item.allows_subscription ?? false,
    salePrice: item.sale_price ?? 0,
    depositAmount: item.deposit_amount ?? 0,
    monthlyRent: item.monthly_rent ?? 0,
    paymentCycleDays: item.payment_cycle_days ?? 0,
    contractTermDays: item.contract_term_days ?? 0,
    ownerKind: item.owner_kind ?? "CLASSROOM",
    ownerStudentId: item.owner_student_id ?? undefined,
    status: item.status ?? "UNKNOWN",
    subscriptionOfferId: item.subscription_offer_id ?? undefined
  }];
}

function mapContract(item: ContractResponse): PropertyContract {
  return {
    contractId: item.contract_id ?? "",
    propertyId: item.property_id ?? "",
    contractType: item.contract_type ?? "RENT",
    depositAmount: item.deposit_amount ?? 0,
    monthlyRent: item.monthly_rent ?? 0,
    salePrice: item.sale_price ?? 0,
    startedAt: item.started_at ?? undefined,
    endsAt: item.ends_at ?? undefined,
    nextPaymentAt: item.next_payment_at ?? undefined,
    status: item.status ?? "PENDING"
  };
}

function mapSubscriptionAccount(item: SubscriptionAccountResponse): SubscriptionAccount {
  return {
    totalDepositAmount: item.total_deposit_amount ?? 0,
    depositCount: item.deposit_count ?? 0,
    lastDepositedAt: item.last_deposited_at ?? undefined,
    autoDepositAmount: item.auto_deposit_amount ?? undefined,
    autoDepositCycleDays: item.auto_deposit_cycle_days ?? undefined,
    nextAutoDepositAt: item.next_auto_deposit_at ?? undefined,
    status: item.status ?? "ACTIVE",
    stateVersion: item.state_version ?? 0
  };
}

export async function fetchAvailableProperties(classroomId: string): Promise<Property[]> {
  if (useMocks) return mockProperties;
  const response = await apiRequest<PropertyResponse[]>(`/classrooms/${classroomId}/properties/available`);
  return response.flatMap(mapProperty);
}

export async function fetchMyProperties(classroomId: string): Promise<Property[]> {
  if (useMocks) return [];
  const response = await apiRequest<PropertyResponse[]>(`/students/me/properties?classroom_id=${classroomId}`);
  return response.flatMap(mapProperty);
}

export async function requestPropertyContract(
  propertyId: string,
  contractType: ContractType,
  subscriptionApplicationId?: string
): Promise<PropertyContract> {
  if (useMocks) {
    return { contractId: "mock-contract", propertyId, contractType, depositAmount: 0, monthlyRent: 0, salePrice: 0, status: "PENDING" };
  }
  const response = await apiRequest<ContractResponse>(`/properties/${propertyId}/contracts`, {
    method: "POST",
    body: JSON.stringify({
      contract_type: contractType,
      subscription_application_id: subscriptionApplicationId ?? null,
      client_occurred_at: new Date().toISOString()
    })
  });
  return mapContract(response);
}

/** 청약통장이 아직 없으면 null을 돌려줍니다. 서버는 이 경우 404를 반환합니다. */
export async function fetchSubscriptionAccount(classroomId: string): Promise<SubscriptionAccount | null> {
  if (useMocks) return null;
  try {
    const response = await apiRequest<SubscriptionAccountResponse>(`/students/me/subscription-account?classroom_id=${classroomId}`);
    return mapSubscriptionAccount(response);
  } catch (error) {
    if (error instanceof ApiError && error.code === "SUBSCRIPTION_ACCOUNT_NOT_FOUND") return null;
    throw error;
  }
}

export async function openSubscriptionAccount(classroomId: string): Promise<SubscriptionAccount> {
  if (useMocks) return { totalDepositAmount: 0, depositCount: 0, status: "ACTIVE", stateVersion: 0 };
  const response = await apiRequest<SubscriptionAccountResponse>("/students/me/subscription-account", {
    method: "POST",
    body: JSON.stringify({
      classroom_id: classroomId,
      client_occurred_at: new Date().toISOString()
    })
  });
  return mapSubscriptionAccount(response);
}

export async function depositSubscription(classroomId: string, amount: number): Promise<SubscriptionAccount> {
  if (useMocks) return { totalDepositAmount: amount, depositCount: 1, status: "ACTIVE", stateVersion: 1 };
  const response = await apiRequest<SubscriptionAccountResponse>("/students/me/subscription-account/deposits", {
    method: "POST",
    body: JSON.stringify({
      classroom_id: classroomId,
      amount,
      client_occurred_at: new Date().toISOString()
    })
  });
  return mapSubscriptionAccount(response);
}

export async function fetchSubscriptionApplications(classroomId: string): Promise<SubscriptionApplication[]> {
  if (useMocks) return [];
  const response = await apiRequest<SubscriptionApplicationResponse[]>(`/students/me/subscription-applications?classroom_id=${classroomId}`);
  return response.flatMap((item) => item.application_id ? [{
    applicationId: item.application_id,
    offerId: item.offer_id ?? "",
    depositCount: item.deposit_count ?? 0,
    depositAmount: item.deposit_amount ?? 0,
    assignedPropertyId: item.assigned_property_id ?? undefined,
    status: item.status ?? "APPLIED",
    appliedAt: item.applied_at ?? undefined
  }] : []);
}

export function contractTypeLabel(type: string) {
  if (type === "RENT") return "월세";
  if (type === "JEONSE") return "전세";
  if (type === "SALE") return "매매";
  return type;
}

export function propertyStatusLabel(status: string) {
  if (status === "AVAILABLE") return "계약 가능";
  if (status === "LISTED_FOR_RENT") return "임대 모집";
  if (status === "SUBSCRIPTION_OPEN") return "청약 진행";
  if (status === "OCCUPIED") return "사용 중";
  if (status === "INACTIVE") return "사용 안 함";
  return status;
}

export function contractStatusLabel(status: string) {
  if (status === "PENDING") return "승인 기다리는 중";
  if (status === "ACTIVE") return "계약 중";
  if (status === "OVERDUE") return "납부 밀림";
  if (status === "TERMINATED") return "계약 끝";
  if (status === "REJECTED") return "거절됨";
  return status;
}

export function applicationStatusLabel(status: string) {
  if (status === "APPLIED") return "신청 완료";
  if (status === "WON") return "당첨";
  if (status === "LOST") return "미당첨";
  return status;
}

/** 자리에서 학생이 지금 신청할 수 있는 계약 종류입니다. */
export function selectableContractTypes(property: Property): ContractType[] {
  if (property.status === "SUBSCRIPTION_OPEN") return property.allowsSale ? ["SALE"] : [];
  const types: ContractType[] = [];
  if (property.allowsRent && property.monthlyRent > 0) types.push("RENT");
  if (property.allowsJeonse && property.depositAmount > 0) types.push("JEONSE");
  if (property.allowsSale && property.salePrice > 0) types.push("SALE");
  return types;
}
