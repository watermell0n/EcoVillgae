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
  termUnits?: number;
  minimumAmount?: number;
  maximumAmount?: number;
  installmentCount?: number;
  installmentAmount?: number;
};

// 서버(FinancialProduct)의 상태값은 DRAFT / PUBLISHED / STOPPED 입니다.
// 학생이 가입할 수 있는 상품은 PUBLISHED 하나뿐이므로 목 데이터도 같은 값을 씁니다.
export const SUBSCRIBABLE_PRODUCT_STATUS = "PUBLISHED";

export async function updateStudentPresence(
  classroomId: string,
  status: "ONLINE" | "AWAY",
  currentScreen: string
) {
  if (useMocks) return;
  await apiRequest(`/classrooms/${classroomId}/students/me/presence`, {
    method: "POST",
    body: JSON.stringify({ status, current_screen: currentScreen })
  });
}

export async function fetchFinancialProducts(classroomId: string): Promise<FinancialProduct[]> {
  if (useMocks) return [
    { resourceId: "mock-deposit-product", label: "3일 예금", status: SUBSCRIBABLE_PRODUCT_STATUS, version: 1, productType: "DEPOSIT", interestRateBps: 1000, termUnits: 3, minimumAmount: 30, maximumAmount: 300 },
    { resourceId: "mock-savings-product", label: "매일 적금", status: SUBSCRIBABLE_PRODUCT_STATUS, version: 1, productType: "SAVINGS", interestRateBps: 1500, termUnits: 5, minimumAmount: 20, maximumAmount: 100, installmentCount: 5, installmentAmount: 20 }
  ];

  const response = await apiRequest<{
    financial_products: Array<{ resource_id?: string; label?: string; status?: string; version?: number; product_type?: string; interest_rate_bps?: number; term_units?: number; minimum_amount?: number; maximum_amount?: number; installment_count?: number | null; installment_amount?: number | null }>;
  }>(`/classrooms/${classroomId}/financial-products`);
  return response.financial_products.flatMap((product) => product.resource_id ? [{
    resourceId: product.resource_id,
    label: product.label ?? "금융 상품",
    status: product.status ?? "DISABLED",
    version: product.version ?? 0,
    productType: product.product_type ?? "UNKNOWN",
    interestRateBps: product.interest_rate_bps ?? 0,
    termUnits: product.term_units ?? undefined,
    minimumAmount: product.minimum_amount ?? undefined,
    maximumAmount: product.maximum_amount ?? undefined,
    installmentCount: product.installment_count ?? undefined,
    installmentAmount: product.installment_amount ?? undefined
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

export type SavingsAccount = {
  resourceId: string;
  productId: string;
  label: string;
  status: string;
  installmentCount: number;
  paidInstallmentCount: number;
  installmentAmount: number;
  principalBalance: number;
  maturesAt: string;
};

export async function fetchFinanceAccounts(classroomId: string) {
  if (useMocks) return { depositAccounts: [] as DepositAccount[], savingsAccounts: [] as SavingsAccount[] };
  const response = await apiRequest<{
    deposit_accounts: Array<{ resource_id?: string; label?: string; status?: string; state_version?: number; principal_amount?: number; matures_at?: string }>;
    savings_accounts: Array<{ resource_id?: string; product_id?: string; label?: string; status?: string; installment_count?: number; paid_installment_count?: number; installment_amount?: number; principal_balance?: number; matures_at?: string }>;
  }>(`/classrooms/${classroomId}/students/me/deposit-and-savings`);
  return {
    depositAccounts: response.deposit_accounts.flatMap((account) => account.resource_id ? [{
      resourceId: account.resource_id,
      label: account.label ?? "예금",
      status: account.status ?? "UNKNOWN",
      stateVersion: account.state_version ?? 0,
      principalAmount: account.principal_amount ?? 0,
      maturesAt: account.matures_at ?? ""
    }] : []),
    savingsAccounts: response.savings_accounts.flatMap((account) => account.resource_id ? [{
      resourceId: account.resource_id,
      productId: account.product_id ?? "",
      label: account.label ?? "적금",
      status: account.status ?? "UNKNOWN",
      installmentCount: account.installment_count ?? 0,
      paidInstallmentCount: account.paid_installment_count ?? 0,
      installmentAmount: account.installment_amount ?? 0,
      principalBalance: account.principal_balance ?? 0,
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

export async function subscribeSavings(classroomId: string, product: FinancialProduct, installmentAmount: number) {
  if (useMocks) return { financialResourceId: randomUUID(), status: "ACTIVE", stateVersion: 1 };
  const response = await apiRequest<{ financial_resource_id: string; status: string; state_version: number }>(`/classrooms/${classroomId}/savings-accounts`, {
    method: "POST",
    retryOnNetworkFailure: true,
    // expected_state_version is omitted on purpose: the server compares it with the
    // classroom state version, which the student app never receives.
    body: JSON.stringify({
      screen_id: "SCR-STU-014",
      ui_action_id: "UIA-FIN-004",
      client_occurred_at: now(),
      product_id: product.resourceId,
      installment_amount: installmentAmount,
      expected_product_version: product.version
    })
  });
  return { financialResourceId: response.financial_resource_id, status: response.status, stateVersion: response.state_version };
}

export type LoanOverview = {
  creditScore: number;
  creditLevel: string;
  availableLimit: number;
  interestRateBps: number;
  termUnits: number;
  installmentCount: number;
  canApply: boolean;
  pendingApplicationId: string | null;
  pendingApplicationStatus: string | null;
  loans: Array<{
    loanId: string;
    principalAmount: number;
    outstandingPrincipal: number;
    outstandingInterest: number;
    appliedInterestRateBps: number;
    status: string;
    maturesAt: string;
    stateVersion: number;
    installments: Array<{
      installmentId: string;
      installmentNumber: number;
      scheduledPrincipal: number;
      scheduledInterest: number;
      dueAt: string;
      graceUntil: string;
      paidAt: string | null;
      status: string;
    }>;
  }>;
  creditEvents: Array<{
    eventId: string;
    eventType: string;
    scoreDelta: number;
    scoreBefore: number;
    scoreAfter: number;
    reason: string;
    occurredAt: string;
  }>;
};

export async function fetchLoanOverview(classroomId: string): Promise<LoanOverview> {
  if (useMocks) return { creditScore: 600, creditLevel: "NORMAL", availableLimit: 240, interestRateBps: 700, termUnits: 28, installmentCount: 4, canApply: true, pendingApplicationId: null, pendingApplicationStatus: null, loans: [], creditEvents: [] };
  const response = await apiRequest<{
    credit_score: number; credit_level: string; available_limit: number; interest_rate_bps: number;
    term_units: number; installment_count: number; can_apply: boolean;
    pending_application_id: string | null; pending_application_status: string | null;
    loans: Array<{
      loan_id: string; principal_amount: number; outstanding_principal: number; outstanding_interest: number;
      applied_interest_rate_bps: number; status: string; matures_at: string; state_version: number;
      installments: Array<{ installment_id: string; installment_number: number; scheduled_principal: number; scheduled_interest: number; due_at: string; grace_until: string; paid_at: string | null; status: string }>;
    }>;
    credit_events: Array<{ event_id: string; event_type: string; score_delta: number; score_before: number; score_after: number; reason: string; occurred_at: string }>;
  }>(`/classrooms/${classroomId}/students/me/loans-and-credit`);
  return {
    creditScore: response.credit_score, creditLevel: response.credit_level, availableLimit: response.available_limit,
    interestRateBps: response.interest_rate_bps, termUnits: response.term_units, installmentCount: response.installment_count,
    canApply: response.can_apply, pendingApplicationId: response.pending_application_id, pendingApplicationStatus: response.pending_application_status,
    loans: response.loans.map((loan) => ({
      loanId: loan.loan_id, principalAmount: loan.principal_amount,
      outstandingPrincipal: loan.outstanding_principal, outstandingInterest: loan.outstanding_interest,
      appliedInterestRateBps: loan.applied_interest_rate_bps, status: loan.status,
      maturesAt: loan.matures_at, stateVersion: loan.state_version,
      installments: loan.installments.map((installment) => ({
        installmentId: installment.installment_id, installmentNumber: installment.installment_number,
        scheduledPrincipal: installment.scheduled_principal, scheduledInterest: installment.scheduled_interest,
        dueAt: installment.due_at, graceUntil: installment.grace_until,
        paidAt: installment.paid_at, status: installment.status
      }))
    })),
    creditEvents: response.credit_events.map((event) => ({
      eventId: event.event_id, eventType: event.event_type, scoreDelta: event.score_delta,
      scoreBefore: event.score_before, scoreAfter: event.score_after,
      reason: event.reason, occurredAt: event.occurred_at
    }))
  };
}

export async function applyLoan(classroomId: string, amount: number, installments: number) {
  if (useMocks) return { applicationId: randomUUID(), status: "PENDING" };
  const response = await apiRequest<{ application_id: string; status: string }>(`/classrooms/${classroomId}/loan-application-commands`, {
    method: "POST", retryOnNetworkFailure: true, body: JSON.stringify({ command: "APPLY", amount, installments })
  });
  return { applicationId: response.application_id, status: response.status };
}

export async function cancelLoanApplication(classroomId: string, applicationId: string) {
  if (useMocks) return { applicationId, status: "CANCELED" };
  const response = await apiRequest<{ application_id: string; status: string }>(`/classrooms/${classroomId}/loan-application-commands`, {
    method: "POST",
    body: JSON.stringify({ command: "CANCEL", application_id: applicationId })
  });
  return { applicationId: response.application_id, status: response.status };
}

export async function repayLoan(classroomId: string, loanId: string, stateVersion: number, command: "RETRY_DUE_PAYMENT" | "PREPAY_ALL") {
  if (useMocks) return { financial_resource_id: loanId, status: command === "PREPAY_ALL" ? "REPAID" : "ACTIVE", state_version: stateVersion + 1, remaining_principal: 0, remaining_interest: 0, credit_score: 620, credit_level: "NORMAL" };
  return apiRequest<{ financial_resource_id: string; status: string; state_version: number; remaining_principal: number; remaining_interest: number; credit_score: number; credit_level: string }>(`/classrooms/${classroomId}/loans/${loanId}/repayment-commands`, {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: stateVersion,
      screen_id: "SCR-STU-019",
      ui_action_id: "UIA-FIN-013",
      client_occurred_at: now(),
      command
    })
  });
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

export type QuizResults = {
  resultVersion: number;
  status: string;
  score: number;
  correctCount: number;
  rank: number;
  rewardAmount: number;
  leaderboard: Array<{ rank: number; nickname: string }>;
};

export async function fetchQuizSession(classroomId: string, quizSessionId: string): Promise<QuizSession> {
  if (useMocks) return {
    quizSessionId,
    mode: "GAME",
    status: "OPEN",
    questions: [
      { resourceId: "mock-question-1", prompt: "내가 일해서 받는 돈을 무엇이라고 할까요?", options: [{ optionId: "q1-1", label: "세금" }, { optionId: "q1-2", label: "월급" }, { optionId: "q1-3", label: "이자" }, { optionId: "q1-4", label: "투자" }] },
      { resourceId: "mock-question-2", prompt: "국가나 지역을 위해 내는 돈은 무엇일까요?", options: [{ optionId: "q2-1", label: "월급" }, { optionId: "q2-2", label: "이자" }, { optionId: "q2-3", label: "세금" }, { optionId: "q2-4", label: "저축" }] },
      { resourceId: "mock-question-3", prompt: "매달 조금씩 돈을 모으는 금융 상품은 무엇일까요?", options: [{ optionId: "q3-1", label: "적금" }, { optionId: "q3-2", label: "대출" }, { optionId: "q3-3", label: "세금" }, { optionId: "q3-4", label: "소비" }] },
      { resourceId: "mock-question-4", prompt: "돈을 빌린 뒤 함께 갚아야 하는 것은 무엇일까요?", options: [{ optionId: "q4-1", label: "이자" }, { optionId: "q4-2", label: "월급" }, { optionId: "q4-3", label: "재고" }, { optionId: "q4-4", label: "예금" }] },
      { resourceId: "mock-question-5", prompt: "상품을 사기 전 가장 먼저 확인할 것은 무엇일까요?", options: [{ optionId: "q5-1", label: "가격과 내 잔액" }, { optionId: "q5-2", label: "친구 이름" }, { optionId: "q5-3", label: "화면 색깔" }, { optionId: "q5-4", label: "광고 문구" }] },
      { resourceId: "mock-question-6", prompt: "투자에는 어떤 가능성이 함께 있을까요?", options: [{ optionId: "q6-1", label: "수익과 손실" }, { optionId: "q6-2", label: "항상 수익" }, { optionId: "q6-3", label: "항상 손실" }, { optionId: "q6-4", label: "변화 없음" }] }
    ]
  };
  const response = await apiRequest<{
    quiz_session_id: string;
    mode: string;
    status: string;
    questions: Array<{
      resource_id?: string;
      prompt?: string;
      // 서버는 보기마다 option_id와 option_text를 내려줍니다. option_id가 없으면 답을 제출할 수
      // 없으므로 선택지를 누를 수 없습니다. 예전 계약(문자열 배열)도 함께 받아 둡니다.
      options?: Array<string | { option_id?: string; option_key?: string; option_text?: string; label?: string }>;
    }>;
  }>(`/classrooms/${classroomId}/quiz-sessions/${quizSessionId}`);
  return {
    quizSessionId: response.quiz_session_id,
    mode: response.mode,
    status: response.status,
    questions: response.questions.flatMap((question) => question.resource_id ? [{
      resourceId: question.resource_id,
      prompt: question.prompt ?? "문제",
      options: (question.options ?? []).map((option) => typeof option === "string"
        ? { label: option }
        : { optionId: option.option_id, label: option.option_text ?? option.label ?? "선택지" })
    }] : [])
  };
}

export async function fetchQuizResults(classroomId: string, quizSessionId: string): Promise<QuizResults> {
  const response = await apiRequest<{
    result_version?: number;
    status?: string;
    summary?: { score?: number; correct_count?: number; rank?: number; reward_amount?: number };
    leaderboard?: Array<{ rank?: number; nickname?: string }>;
  }>(`/classrooms/${classroomId}/quiz-sessions/${quizSessionId}/results`);
  return {
    resultVersion: response.result_version ?? 0,
    status: response.status ?? "PUBLISHED",
    score: response.summary?.score ?? 0,
    correctCount: response.summary?.correct_count ?? 0,
    rank: response.summary?.rank ?? 0,
    rewardAmount: response.summary?.reward_amount ?? 0,
    leaderboard: (response.leaderboard ?? []).map((entry) => ({
      rank: entry.rank ?? 0,
      nickname: entry.nickname ?? "학생"
    }))
  };
}

export type TestAnswerSheetEntry = { questionId: string; optionId: string };

/**
 * 게임형 답 제출입니다. 한 문항에 한 번만 낼 수 있고, 서버가 그 규칙을 지킵니다.
 */
export async function submitGameAnswer(
  classroomId: string,
  sessionId: string,
  questionId: string,
  optionId: string
) {
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

/**
 * 평가형 답안지를 저장합니다.
 *
 * <p>서버는 답안지를 판본 단위로 통째로 보관합니다. 그래서 지금까지 고른 답을 **모두** 실어
 * 보내야 하고, 보낸 판본이 서버가 가진 판본과 같아야 저장됩니다. 한 문항만 보내면 앞서 고른
 * 답이 사라지고, 판본을 0으로 고정해 보내면 두 번째 문항부터 STATE_VERSION_CONFLICT로
 * 거절됩니다. 저장에 성공하면 서버가 올려 준 판본을 돌려주므로 다음 저장에 그대로 씁니다.
 *
 * @param submit 마지막 제출이면 true입니다. 그 전에는 임시 저장으로 둡니다.
 * @returns 다음 저장에 쓸 답안지 판본
 */
export async function saveTestAnswerSheet(
  classroomId: string,
  sessionId: string,
  answers: readonly TestAnswerSheetEntry[],
  answerSheetVersion: number,
  submit: boolean
): Promise<number> {
  if (useMocks) return answerSheetVersion + 1;
  const response = await apiRequest<{ state_version?: number }>(
    `/classrooms/${classroomId}/quiz-sessions/${sessionId}/test-answer-sheet`,
    {
      method: "PUT",
      retryOnNetworkFailure: true,
      body: JSON.stringify({
        screen_id: "SCR-STU-021",
        ui_action_id: "UIA-QZ-005",
        client_occurred_at: now(),
        answers: answers.map((answer) => ({
          question_id: answer.questionId,
          option_id: answer.optionId
        })),
        answer_sheet_version: answerSheetVersion,
        submit
      })
    }
  );
  return response.state_version ?? answerSheetVersion + 1;
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
  if (useMocks) return { voteId, title: "공동기금 사용처 투표", description: "우리 반 공동기금을 어디에 쓸지 골라요.", status: "OPEN", stateVersion: 1, publicFundBalance: 340, hasVoted: false, options: [{ optionId: "mock-snack", label: "간식 타임", description: "다 같이 먹을 간식을 준비해요.", budgetAmount: 180 }, { optionId: "mock-movie", label: "영화 보기", description: "우리 반이 함께 볼 영화를 골라요.", budgetAmount: 300 }] };
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
  if (useMocks) return { activityId, status: "LIVE", participantCount: 24, activityType: "ECONOMIC_CONCEPT_IMAGE", presentationTitle: "경제 개념 수업", presentationImageUrl: undefined };
  const response = await apiRequest<{
    activity_id: string;
    status: string;
    participant_count: number;
    activity_type?: string;
    presentation_title?: string;
    presentation_image_url?: string;
  }>(`/classrooms/${classroomId}/guided-activities/${activityId}`);
  return {
    activityId: response.activity_id,
    status: response.status,
    participantCount: response.participant_count,
    activityType: response.activity_type,
    presentationTitle: response.presentation_title,
    presentationImageUrl: response.presentation_image_url
  };
}

export type InventoryItem = { resourceId: string; label: string; status: string; stateVersion: number; characterCode: string; equipped: boolean };

export async function fetchInventory(classroomId: string): Promise<InventoryItem[]> {
  if (useMocks) return [
    { resourceId: "mock-cap", label: "빨간 캡", status: "AVAILABLE", stateVersion: 1, characterCode: "hat_red_cap", equipped: true },
    { resourceId: "mock-hoodie", label: "노란 후드", status: "AVAILABLE", stateVersion: 1, characterCode: "top_yellow_hoodie", equipped: true },
    { resourceId: "mock-sneakers", label: "운동화", status: "AVAILABLE", stateVersion: 1, characterCode: "shoes_sneakers", equipped: false },
    { resourceId: "mock-backpack", label: "노란 배낭", status: "AVAILABLE", stateVersion: 1, characterCode: "back_backpack", equipped: false },
    { resourceId: "mock-sunglasses", label: "선글라스", status: "AVAILABLE", stateVersion: 1, characterCode: "face_sunglasses", equipped: false }
  ];
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

export async function setInventoryEquipped(classroomId: string, item: InventoryItem, equipped: boolean) {
  if (useMocks) return;

  await apiRequest(`/classrooms/${classroomId}/inventory-items/${item.resourceId}/commands`, {
    method: "POST",
    body: JSON.stringify({
      command: equipped ? "EQUIP" : "UNEQUIP",
      expected_state_version: item.stateVersion,
      screen_id: "SCR-STU-008",
      ui_action_id: "UIA-ITM-001",
      client_occurred_at: new Date().toISOString()
    })
  });
}
