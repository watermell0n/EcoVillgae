import { randomUUID } from "expo-crypto";

import { apiRequest } from "@/api/client";

const useMocks = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true";

function now() {
  return new Date().toISOString();
}

export type WalletTransaction = {
  resourceId: string;
  label: string;
  status: string;
  amount: number;
  occurredAt: string;
};

export type Wallet = { balance: number; recentTransactions: WalletTransaction[] };

export async function fetchWallet(classroomId: string): Promise<Wallet> {
  if (useMocks) {
    return {
      balance: 158,
      recentTransactions: [
        { resourceId: "mock-wage", label: "1인 1역 월급", status: "COMMITTED", amount: 150, occurredAt: now() },
        { resourceId: "mock-quiz-reward", label: "퀴즈 정답수당", status: "COMMITTED", amount: 25, occurredAt: now() },
        { resourceId: "mock-income-tax", label: "소득세", status: "COMMITTED", amount: -17, occurredAt: now() }
      ]
    };
  }

  const response = await apiRequest<{
    balance: number;
    recent_transactions: Array<{ resource_id?: string; label?: string; status?: string; amount?: number; occurred_at?: string }>;
  }>(`/classrooms/${classroomId}/students/me/wallet`);
  return {
    balance: response.balance,
    recentTransactions: response.recent_transactions.map((transaction) => ({
      resourceId: transaction.resource_id ?? randomUUID(),
      label: transaction.label ?? "거래",
      status: transaction.status ?? "COMMITTED",
      amount: transaction.amount ?? 0,
      occurredAt: transaction.occurred_at ?? now()
    }))
  };
}

export type FinancialProduct = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  productType: string;
  interestRateBps: number;
};

// 서버(FinancialProduct)의 상태값은 DRAFT / PUBLISHED / STOPPED 입니다.
// 학생이 가입할 수 있는 상품은 PUBLISHED 하나뿐이므로 목 데이터도 같은 값을 씁니다.
export const SUBSCRIBABLE_PRODUCT_STATUS = "PUBLISHED";

export async function fetchFinancialProducts(classroomId: string): Promise<FinancialProduct[]> {
  if (useMocks) return [
    { resourceId: "mock-deposit-product", label: "3일 예금", status: SUBSCRIBABLE_PRODUCT_STATUS, version: 1, productType: "DEPOSIT", interestRateBps: 1000 },
    { resourceId: "mock-savings-product", label: "매일 적금", status: SUBSCRIBABLE_PRODUCT_STATUS, version: 1, productType: "SAVINGS", interestRateBps: 1500 }
  ];

  const response = await apiRequest<{
    financial_products: Array<{ resource_id?: string; label?: string; status?: string; version?: number; product_type?: string; interest_rate_bps?: number }>;
  }>(`/classrooms/${classroomId}/financial-products`);
  return response.financial_products.flatMap((product) => product.resource_id ? [{
    resourceId: product.resource_id,
    label: product.label ?? "금융 상품",
    status: product.status ?? "DISABLED",
    version: product.version ?? 0,
    productType: product.product_type ?? "UNKNOWN",
    interestRateBps: product.interest_rate_bps ?? 0
  }] : []);
}

export type DepositAccount = {
  resourceId: string;
  label: string;
  status: string;
  stateVersion: number;
  principalAmount: number;
  maturesAt: string;
};

export async function fetchFinanceAccounts(classroomId: string) {
  if (useMocks) return { depositAccounts: [] as DepositAccount[] };
  const response = await apiRequest<{
    deposit_accounts: Array<{ resource_id?: string; label?: string; status?: string; state_version?: number; principal_amount?: number; matures_at?: string }>;
  }>(`/classrooms/${classroomId}/students/me/deposit-and-savings`);
  return {
    depositAccounts: response.deposit_accounts.flatMap((account) => account.resource_id ? [{
      resourceId: account.resource_id,
      label: account.label ?? "예금",
      status: account.status ?? "UNKNOWN",
      stateVersion: account.state_version ?? 0,
      principalAmount: account.principal_amount ?? 0,
      maturesAt: account.matures_at ?? ""
    }] : [])
  };
}

export async function subscribeDeposit(classroomId: string, product: FinancialProduct, principal: number) {
  if (useMocks) return { financialResourceId: randomUUID(), status: "ACTIVE", stateVersion: 1 };
  const response = await apiRequest<{ financial_resource_id: string; status: string; state_version: number }>(`/classrooms/${classroomId}/deposit-accounts`, {
    method: "POST",
    retryOnNetworkFailure: true,
    // expected_state_version is omitted on purpose: the server compares it with the
    // classroom state version, which the student app never receives.
    body: JSON.stringify({
      screen_id: "SCR-STU-014",
      ui_action_id: "UIA-FIN-002",
      client_occurred_at: now(),
      product_id: product.resourceId,
      principal,
      expected_product_version: product.version
    })
  });
  return { financialResourceId: response.financial_resource_id, status: response.status, stateVersion: response.state_version };
}

export async function createTrade(classroomId: string, counterpartStudentId: string, description: string, amount: number) {
  if (useMocks) return { tradeId: randomUUID(), status: "PENDING_COUNTERPART", stateVersion: 1 };
  const response = await apiRequest<{ trade_id: string; status: string; state_version: number }>(`/classrooms/${classroomId}/trades`, {
    method: "POST",
    retryOnNetworkFailure: true,
    // expected_state_version is omitted on purpose: the server compares it with the
    // classroom state version, which the student app never receives.
    body: JSON.stringify({
      screen_id: "SCR-STU-010",
      ui_action_id: "UIA-TRD-001",
      client_occurred_at: now(),
      counterpart_student_id: counterpartStudentId,
      offered_amount: amount,
      requested_amount: 0,
      offered_item_ids: [],
      requested_item_ids: [],
      description
    })
  });
  return { tradeId: response.trade_id, status: response.status, stateVersion: response.state_version };
}

export type QuizOption = { optionId?: string; label: string };
export type QuizSession = {
  quizSessionId: string;
  mode: string;
  status: string;
  questions: Array<{ resourceId: string; prompt: string; options: QuizOption[] }>;
};

export async function fetchQuizSession(classroomId: string, quizSessionId: string): Promise<QuizSession> {
  if (useMocks) return {
    quizSessionId,
    mode: "GAME",
    status: "OPEN",
    questions: [{ resourceId: "mock-question", prompt: "소득세는 어디로 모이나요?", options: [
      { optionId: "mock-wallet", label: "내 지갑" },
      { optionId: "mock-public-fund", label: "우리 반 공동기금" },
      { optionId: "mock-shop", label: "상점 주인" }
    ] }]
  };
  const response = await apiRequest<{
    quiz_session_id: string;
    mode: string;
    status: string;
    questions: Array<{ resource_id?: string; prompt?: string; options?: Array<string | { option_id?: string; label?: string }> }>;
  }>(`/classrooms/${classroomId}/quiz-sessions/${quizSessionId}`);
  return {
    quizSessionId: response.quiz_session_id,
    mode: response.mode,
    status: response.status,
    questions: response.questions.flatMap((question) => question.resource_id ? [{
      resourceId: question.resource_id,
      prompt: question.prompt ?? "문제",
      options: (question.options ?? []).map((option) => typeof option === "string" ? { label: option } : { optionId: option.option_id, label: option.label ?? "선택지" })
    }] : [])
  };
}

export async function submitQuizAnswer(classroomId: string, sessionId: string, questionId: string, optionId: string) {
  if (useMocks) return { submitted: true };
  return apiRequest<unknown>(`/classrooms/${classroomId}/quiz-sessions/${sessionId}/game-answers`, {
    method: "POST",
    retryOnNetworkFailure: true,
    // expected_state_version is omitted on purpose: the server compares it with the
    // quiz session state version, which the student app never receives.
    body: JSON.stringify({
      screen_id: "SCR-STU-021",
      ui_action_id: "UIA-QZ-005",
      client_occurred_at: now(),
      question_id: questionId,
      option_id: optionId,
      client_answered_at: now()
    })
  });
}

export type Vote = {
  voteId: string;
  title: string;
  description: string;
  status: string;
  stateVersion: number;
  publicFundBalance: number;
  hasVoted: boolean;
  options: Array<{ optionId: string; label: string; description: string; budgetAmount: number }>;
};

export async function fetchVote(classroomId: string, voteId: string): Promise<Vote> {
  if (useMocks) return { voteId, title: "공동기금 사용처 투표", description: "우리 반 공동기금을 어디에 쓸지 골라요.", status: "OPEN", stateVersion: 1, publicFundBalance: 340, hasVoted: false, options: [{ optionId: "mock-snack", label: "학급 간식", description: "다 같이 먹을 간식을 사요.", budgetAmount: 180 }] };
  const response = await apiRequest<{
    vote_id: string;
    title: string;
    description?: string | null;
    status: string;
    state_version: number;
    public_fund_balance: number;
    has_voted?: boolean | null;
    options: Array<{ option_id: string; label: string; description?: string | null; budget_amount: number }>;
  }>(`/classrooms/${classroomId}/votes/${voteId}`);
  return {
    voteId: response.vote_id,
    title: response.title,
    description: response.description ?? "",
    status: response.status,
    stateVersion: response.state_version,
    publicFundBalance: response.public_fund_balance,
    hasVoted: response.has_voted ?? false,
    options: response.options.map((option) => ({ optionId: option.option_id, label: option.label, description: option.description ?? "", budgetAmount: option.budget_amount }))
  };
}

export async function castVote(classroomId: string, vote: Vote, optionId: string) {
  if (useMocks) return { receiptHash: randomUUID(), castAt: now() };
  return apiRequest<{ receipt_hash: string; cast_at: string }>(`/classrooms/${classroomId}/votes/${vote.voteId}/ballots`, {
    method: "POST",
    retryOnNetworkFailure: true,
    body: JSON.stringify({ expected_state_version: vote.stateVersion, screen_id: "SCR-STU-012", ui_action_id: "UIA-VOT-002", client_occurred_at: now(), option_id: optionId })
  });
}

export async function fetchGuidedActivity(classroomId: string, activityId: string) {
  if (useMocks) return { activityId, status: "LIVE", participantCount: 24 };
  const response = await apiRequest<{ activity_id: string; status: string; participant_count: number }>(`/classrooms/${classroomId}/guided-activities/${activityId}`);
  return { activityId: response.activity_id, status: response.status, participantCount: response.participant_count };
}

export type InventoryItem = { resourceId: string; label: string; status: string; stateVersion: number; characterCode: string; equipped: boolean };

export async function fetchInventory(classroomId: string): Promise<InventoryItem[]> {
  if (useMocks) return [{ resourceId: "mock-cap", label: "탐험가 모자", status: "AVAILABLE", stateVersion: 1, characterCode: "HEAD_EXPLORER_CAP", equipped: false }];
  const response = await apiRequest<{ inventory_items: Array<{ resource_id?: string; label?: string; status?: string; state_version?: number; character_code?: string; equipped?: boolean }> }>(`/classrooms/${classroomId}/students/me/inventory`);
  return response.inventory_items.flatMap((item) => item.resource_id ? [{
    resourceId: item.resource_id,
    label: item.label ?? "아이템",
    status: item.status ?? "UNKNOWN",
    stateVersion: item.state_version ?? 0,
    characterCode: item.character_code ?? "",
    equipped: item.equipped ?? false
  }] : []);
}

export async function commandInventoryItem(
  classroomId: string,
  item: InventoryItem,
  command: "EQUIP" | "UNEQUIP"
) {
  if (useMocks) return { equipped: command === "EQUIP" };
  const response = await apiRequest<{ equipped?: boolean }>(
    `/classrooms/${classroomId}/inventory-items/${item.resourceId}/commands`,
    {
      method: "POST",
      retryOnNetworkFailure: true,
      body: JSON.stringify({
        // 서버가 인벤토리 항목의 state_version과 대조하므로 조회한 값을 그대로 보낸다.
        expected_state_version: item.stateVersion,
        screen_id: "SCR-STU-008",
        ui_action_id: command === "EQUIP" ? "UIA-CHR-001" : "UIA-CHR-002",
        client_occurred_at: now(),
        command
      })
    }
  );
  return { equipped: response.equipped ?? command === "EQUIP" };
}
