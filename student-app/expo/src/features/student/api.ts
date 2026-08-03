import { randomUUID } from "expo-crypto";

import { apiRequest } from "@/api/client";

const useMocks = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true";
const ids = {
  depositProduct: "00000000-0000-4000-8000-000000002001",
  counterpart: "00000000-0000-4000-8000-000000003002",
  quizSession: "00000000-0000-4000-8000-000000004001",
  quizQuestion: "00000000-0000-4000-8000-000000004002",
  quizOption: "00000000-0000-4000-8000-000000004003",
  vote: "00000000-0000-4000-8000-000000005001",
  voteOption: "00000000-0000-4000-8000-000000005002"
};

function now() {
  return new Date().toISOString();
}

export type DepositAccount = {
  resourceId: string;
  label: string;
  status: string;
  stateVersion: number;
  principalAmount: number;
  maturesAt: string;
};

type DepositAccountResponse = {
  resource_id: string;
  label: string;
  status: string;
  state_version: number;
  principal_amount: number;
  matures_at: string;
};

export async function fetchFinanceAccounts(classroomId: string) {
  if (useMocks) {
    return { depositAccounts: [{ resourceId: "mock-deposit", label: "3일 예금", status: "ACTIVE", stateVersion: 1, principalAmount: 100, maturesAt: new Date(Date.now() + 172_800_000).toISOString() }] as DepositAccount[] };
  }
  const response = await apiRequest<{ deposit_accounts: DepositAccountResponse[] }>(`/classrooms/${classroomId}/students/me/deposit-and-savings`);
  return {
    depositAccounts: response.deposit_accounts.map((account) => ({
      resourceId: account.resource_id,
      label: account.label,
      status: account.status,
      stateVersion: account.state_version,
      principalAmount: account.principal_amount,
      maturesAt: account.matures_at
    }))
  };
}

export async function subscribeDeposit(classroomId: string, principal: number) {
  if (useMocks) return { financialResourceId: randomUUID(), status: "ACTIVE", stateVersion: 1 };
  const response = await apiRequest<{ financial_resource_id: string; status: string; state_version: number }>(`/classrooms/${classroomId}/deposit-accounts`, {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: 0,
      screen_id: "SCR-STU-014",
      ui_action_id: "SUBSCRIBE_DEPOSIT",
      client_occurred_at: now(),
      product_id: ids.depositProduct,
      principal,
      expected_product_version: 1
    })
  });
  return {
    financialResourceId: response.financial_resource_id,
    status: response.status,
    stateVersion: response.state_version
  };
}

export async function createTrade(classroomId: string, description: string, amount: number) {
  if (useMocks) return { tradeId: randomUUID(), status: "PENDING_COUNTERPART", stateVersion: 1 };
  const response = await apiRequest<{ trade_id: string; status: string; state_version: number }>(`/classrooms/${classroomId}/trades`, {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: 0,
      screen_id: "SCR-STU-010",
      ui_action_id: "CREATE_TRADE",
      client_occurred_at: now(),
      counterpart_student_id: ids.counterpart,
      offered_amount: amount,
      requested_amount: 0,
      offered_item_ids: [],
      requested_item_ids: [],
      description
    })
  });
  return { tradeId: response.trade_id, status: response.status, stateVersion: response.state_version };
}

export type QuizSession = {
  quizSessionId: string;
  mode: string;
  status: string;
  questions: Array<{ resourceId: string; prompt: string; options: string[] }>;
};

export async function fetchQuizSession(classroomId: string) {
  if (useMocks) {
    return { quizSessionId: ids.quizSession, mode: "GAME", status: "OPEN", questions: [{ resourceId: ids.quizQuestion, prompt: "소득세는 어디로 모이나요?", options: ["내 지갑", "우리 반 공동기금", "상점 주인"] }] } satisfies QuizSession;
  }
  const response = await apiRequest<{
    quiz_session_id: string;
    mode: string;
    status: string;
    questions: Array<{ resource_id: string; prompt: string; options: string[] }>;
  }>(`/classrooms/${classroomId}/quiz-sessions/${ids.quizSession}`);
  return {
    quizSessionId: response.quiz_session_id,
    mode: response.mode,
    status: response.status,
    questions: response.questions.map((question) => ({
      resourceId: question.resource_id,
      prompt: question.prompt,
      options: question.options
    }))
  };
}

export async function submitQuizAnswer(classroomId: string, sessionId: string, questionId: string) {
  if (useMocks) return { submitted: true };
  return apiRequest<unknown>(`/classrooms/${classroomId}/quiz-sessions/${sessionId}/game-answers`, {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: 0,
      screen_id: "SCR-STU-021",
      ui_action_id: "SUBMIT_GAME_ANSWER",
      client_occurred_at: now(),
      question_id: questionId,
      option_id: ids.quizOption,
      client_answered_at: now()
    })
  });
}

export type Vote = {
  voteId: string;
  title: string;
  description: string;
  status: string;
  publicFundBalance: number;
  options: Array<{ optionId: string; label: string; description: string; budgetAmount: number }>;
};

export async function fetchVote(classroomId: string) {
  if (useMocks) {
    return { voteId: ids.vote, title: "공동기금 사용처 투표", description: "우리 반 공동기금을 어디에 쓸지 골라요.", status: "OPEN", publicFundBalance: 340, options: [{ optionId: ids.voteOption, label: "학급 간식", description: "다 같이 먹을 간식을 사요.", budgetAmount: 180 }, { optionId: "00000000-0000-4000-8000-000000005003", label: "교실 꾸미기", description: "마을 게시판을 꾸며요.", budgetAmount: 120 }] } satisfies Vote;
  }
  const response = await apiRequest<{
    vote_id: string;
    title: string;
    description: string;
    status: string;
    public_fund_balance: number;
    options: Array<{ option_id: string; label: string; description: string; budget_amount: number }>;
  }>(`/classrooms/${classroomId}/votes/${ids.vote}`);
  return {
    voteId: response.vote_id,
    title: response.title,
    description: response.description,
    status: response.status,
    publicFundBalance: response.public_fund_balance,
    options: response.options.map((option) => ({
      optionId: option.option_id,
      label: option.label,
      description: option.description,
      budgetAmount: option.budget_amount
    }))
  };
}

export async function castVote(classroomId: string, voteId: string, optionId: string) {
  if (useMocks) return { receiptHash: randomUUID(), castAt: now() };
  return apiRequest<{ receiptHash: string; castAt: string }>(`/classrooms/${classroomId}/votes/${voteId}/ballots`, {
    method: "POST",
    body: JSON.stringify({ expected_state_version: 1, screen_id: "SCR-STU-012", ui_action_id: "CAST_BALLOT", client_occurred_at: now(), option_id: optionId })
  });
}

export type InventoryItem = {
  resourceId: string;
  label: string;
  status: string;
  stateVersion: number;
  characterCode: string;
  equipped: boolean;
};

export async function fetchInventory(classroomId: string) {
  if (useMocks) return [{ resourceId: "mock-cap", label: "탐험가 모자", status: "AVAILABLE", stateVersion: 1, characterCode: "HEAD_EXPLORER_CAP", equipped: false }] satisfies InventoryItem[];
  const response = await apiRequest<{
    inventory_items: Array<{
      resource_id: string;
      label: string;
      status: string;
      state_version: number;
      character_code: string;
      equipped: boolean;
    }>;
  }>(`/classrooms/${classroomId}/students/me/inventory`);
  return response.inventory_items.map((item) => ({
    resourceId: item.resource_id,
    label: item.label,
    status: item.status,
    stateVersion: item.state_version,
    characterCode: item.character_code,
    equipped: item.equipped
  }));
}
