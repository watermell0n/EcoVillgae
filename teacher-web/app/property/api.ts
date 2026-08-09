import { apiRequest } from "../api";

export type ClassroomProperty = {
  property_id: string;
  coordinate: string;
  display_name: string;
  tier: string;
  is_public: boolean;
  is_active: boolean;
  allows_rent: boolean;
  allows_jeonse: boolean;
  allows_sale: boolean;
  allows_subscription: boolean;
  sale_price: number;
  deposit_amount: number;
  rent_deposit_amount: number;
  monthly_rent: number;
  payment_cycle_days: number;
  contract_term_days: number;
  owner_kind: "TEACHER" | "STUDENT";
  owner_student_id?: string | null;
  status: string;
  subscription_offer_id?: string | null;
};

export type PropertyLayout = {
  row_count: number;
  column_count: number;
  state_version: number;
  properties: ClassroomProperty[];
  can_rebuild_layout?: boolean;
  rebuild_blocked_reason?: string | null;
};

export type PendingPropertyContract = {
  contract_id: string;
  property_id: string;
  contract_type: string;
  occupant_student_id: string;
  landlord_kind: string;
  landlord_student_id?: string | null;
  deposit_amount: number;
  monthly_rent: number;
  sale_price: number;
  status: string;
  coordinate: string;
  display_name: string;
  student_nickname: string;
  roster_number: number;
};

export type SubscriptionOffer = {
  offer_id: string;
  classroom_id: string;
  title: string;
  opens_at: string;
  closes_at: string;
  sale_price: number;
  supply_quantity: number;
  minimum_deposit_count: number;
  minimum_balance: number;
  status: string;
  applicant_count: number;
  property_ids: string[];
};

const commandMetadata = () => ({
  screen_id: "SCR-WEB-PROPERTY",
  ui_action_id: crypto.randomUUID(),
  client_occurred_at: new Date().toISOString()
});

export function fetchPropertyLayout(classroomId: string) {
  return apiRequest<PropertyLayout>(`/classrooms/${classroomId}/property-layout`);
}

export function replacePropertyLayout(classroomId: string, rows: number, columns: number, stateVersion: number) {
  return apiRequest<PropertyLayout>(`/classrooms/${classroomId}/property-layout`, {
    method: "PUT",
    body: JSON.stringify({ ...commandMetadata(), expected_state_version: stateVersion, rows, columns })
  });
}

export function updateClassroomProperty(property: ClassroomProperty, changes: Partial<ClassroomProperty>) {
  const next = { ...property, ...changes };
  return apiRequest<ClassroomProperty>(`/properties/${property.property_id}`, {
    method: "PUT",
    body: JSON.stringify({
      ...commandMetadata(),
      display_name: next.display_name,
      tier: next.tier,
      is_public: next.is_public,
      active: next.is_active,
      allows_rent: next.allows_rent,
      allows_jeonse: next.allows_jeonse,
      allows_sale: next.allows_sale,
      allows_subscription: next.allows_subscription,
      sale_price: next.sale_price,
      deposit_amount: next.deposit_amount,
      rent_deposit_amount: next.rent_deposit_amount,
      monthly_rent: next.monthly_rent,
      payment_cycle_days: next.payment_cycle_days,
      contract_term_days: next.contract_term_days
    })
  });
}

export function fetchPendingPropertyContracts(classroomId: string) {
  return apiRequest<PendingPropertyContract[]>(`/classrooms/${classroomId}/property-contracts`);
}

export function decidePropertyContract(contractId: string, approve: boolean) {
  return apiRequest(`/contracts/${contractId}/${approve ? "approve" : "reject"}`, {
    method: "POST",
    body: JSON.stringify({ ...commandMetadata(), reason: approve ? "교사 승인" : "교사 거절" })
  });
}

export function fetchSubscriptionOffers(classroomId: string) {
  return apiRequest<SubscriptionOffer[]>(`/classrooms/${classroomId}/subscription-offers`);
}

export function createSubscriptionOffer(classroomId: string, input: {
  title: string;
  closesAt: string;
  salePrice: number;
  minimumDepositCount: number;
  minimumBalance: number;
  propertyIds: string[];
}) {
  return apiRequest<SubscriptionOffer>(`/classrooms/${classroomId}/subscription-offers`, {
    method: "POST",
    body: JSON.stringify({
      ...commandMetadata(),
      title: input.title,
      opens_at: new Date(Date.now() - 60_000).toISOString(),
      closes_at: new Date(input.closesAt).toISOString(),
      sale_price: input.salePrice,
      supply_quantity: input.propertyIds.length,
      minimum_deposit_count: input.minimumDepositCount,
      minimum_balance: input.minimumBalance,
      property_ids: input.propertyIds
    })
  });
}

export function drawSubscriptionOffer(offerId: string) {
  return apiRequest(`/subscription-offers/${offerId}/draw`, {
    method: "POST",
    body: JSON.stringify(commandMetadata())
  });
}

export function cancelSubscriptionOffer(offerId: string) {
  return apiRequest(`/subscription-offers/${offerId}/cancel`, {
    method: "POST",
    body: JSON.stringify(commandMetadata())
  });
}
