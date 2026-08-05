import { apiRequest } from "./client";
import { createUuid } from "./uuid";

const MOCK_FEATURES = import.meta.env.VITE_USE_MOCK_STUDENT_FEATURES === "true";

export type StorePurchaseResponse = {
  orderId: string;
  status: string;
  totalAmount: number;
  stateVersion: number;
};

type StorePurchaseWireResponse = {
  order_id: string;
  status: string;
  total_amount: number;
  state_version: number;
};

export type DepositAccountView = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  stateVersion: number;
  principalAmount: number;
  expectedInterestAmount: number;
  maturityAmount: number;
  openedAt: string;
  maturesAt: string;
  closedAt: string | null;
};

export type FinancialProductView = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  stateVersion: number;
  productType: string;
  termUnits: number;
  interestRateBps: number;
  earlyTerminationRateBps: number;
  minimumAmount: number;
  maximumAmount: number;
};

export type DepositAndSavingsResponse = {
  depositAccounts: DepositAccountView[];
  savingsAccounts: unknown[];
};

export type FinancialResourceResponse = {
  financialResourceId: string;
  status: string;
  stateVersion: number;
};

type RawFinancialProductView = {
  resource_id: string;
  label: string;
  status: string;
  version: number;
  state_version: number;
  product_type: string;
  term_units: number;
  interest_rate_bps: number;
  early_termination_rate_bps: number;
  minimum_amount: number;
  maximum_amount: number;
};

type RawDepositAccountView = {
  resource_id: string;
  label: string;
  status: string;
  version: number;
  state_version: number;
  principal_amount: number;
  expected_interest_amount: number;
  maturity_amount: number;
  opened_at: string;
  matures_at: string;
  closed_at: string | null;
};

export type TradeCommandResponse = {
  tradeId: string;
  status: string;
  stateVersion: number;
};

export type QuizQuestion = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  stateVersion: number;
  contentType: string;
  questionType: string;
  prompt: string;
};

export type QuizSessionQuestion = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  stateVersion: number;
  prompt: string;
  options: string[];
};

export type QuizSession = {
  quizSessionId: string;
  mode: string;
  status: string;
  questions: QuizSessionQuestion[];
};

export type VoteOption = {
  optionId: string;
  label: string;
  description: string;
  budgetAmount: number;
  voteCount: number | null;
};

export type VoteView = {
  voteId: string;
  title: string;
  description: string;
  anonymous: boolean;
  status: string;
  stateVersion: number;
  opensAt: string;
  closesAt: string;
  publicFundBalance: number;
  participantCount: number;
  ballotCount: number;
  eligible: boolean | null;
  hasVoted: boolean | null;
  selectedOptionId: string | null;
  selectedUse: string | null;
  transactionGroupId: string | null;
  options: VoteOption[];
};

export type GuidedActivity = {
  activityId: string;
  status: string;
  participantCount: number;
};

export type InvestmentMode = "SHORT_TERM" | "LONG_TERM";

export type InvestmentModuleState = {
  active: boolean;
  investmentMode: InvestmentMode;
};

type RawClassroomModule = {
  module_code: string;
  status: string;
};

export async function fetchActiveModuleCodes(classroomId: string) {
  if (MOCK_FEATURES) {
    return new Set([
      "STORE", "DEPOSIT", "SAVINGS", "LOAN", "INVESTMENT", "TRADE", "QUIZ", "ROLE_SALARY"
    ]);
  }

  const response = await apiRequest<{ modules: RawClassroomModule[] }>(
    `/classrooms/${classroomId}/modules-and-policies`
  );
  return new Set(
    response.modules
      .filter((module) => module.status === "ACTIVE")
      .map((module) => module.module_code)
  );
}


const ids = {
  product: "00000000-0000-4000-8000-000000001001",
  depositProduct: "00000000-0000-4000-8000-000000002001",
  trade: "00000000-0000-4000-8000-000000003001",
  counterpart: "00000000-0000-4000-8000-000000003002",
  quizSession: "00000000-0000-4000-8000-000000004001",
  quizQuestion: "00000000-0000-4000-8000-000000004002",
  quizOption: "00000000-0000-4000-8000-000000004003",
  vote: "00000000-0000-4000-8000-000000005001",
  voteOption: "00000000-0000-4000-8000-000000005002",
  guidedActivity: "00000000-0000-4000-8000-000000006001"
};

export const demoIds = ids;

function now() {
  return new Date().toISOString();
}

export async function fetchInvestmentModuleState(
  classroomId: string
): Promise<InvestmentModuleState> {
  if (MOCK_FEATURES) {
    return { active: true, investmentMode: "LONG_TERM" };
  }

  const response = await apiRequest<{
    modules?: Array<{
      module_code?: string;
      moduleCode?: string;
      status?: string;
      config?: { investment_mode?: string; investmentMode?: string };
    }>;
  }>(`/classrooms/${classroomId}/modules-and-policies`);
  const investment = response.modules?.find(
    (module) => (module.module_code ?? module.moduleCode) === "INVESTMENT"
  );
  const configuredMode = investment?.config?.investment_mode
    ?? investment?.config?.investmentMode;
  return {
    active: investment?.status === "ACTIVE",
    investmentMode: configuredMode === "SHORT_TERM" ? "SHORT_TERM" : "LONG_TERM"
  };
}

export function calculateStoreTotal(
  unitPrice: number,
  quantity = 1,
  vatRateBps = 0
) {
  const subtotal = unitPrice * quantity;
  const tax = Math.floor((subtotal * vatRateBps + 5_000) / 10_000);
  return subtotal + tax;
}

export async function purchaseStoreItem(
  classroomId: string,
  productId = ids.product,
  quantity = 1,
  expectedProductVersion = 1,
  mockUnitPrice = 50,
  mockVatRateBps = 0,
  idempotencyKey: string = createUuid()
) {
  if (MOCK_FEATURES) {
    return {
      orderId: createUuid(),
      status: "CREATED",
      totalAmount: calculateStoreTotal(mockUnitPrice, quantity, mockVatRateBps),
      stateVersion: 1
    } satisfies StorePurchaseResponse;
  }

  const purchase = await apiRequest<StorePurchaseWireResponse>(`/classrooms/${classroomId}/store/orders`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({
      // 계약(API-SHP-002)이 요구하는 형식은 `^SCR-[A-Z]+-\d{3}$` / `^UIA-[A-Z]+-\d{3}$`다.
      // "SCR-STU-SHOP"·"BUY_PRODUCT"는 형식을 벗어나 스키마 검증에서 걸린다.
      screen_id: "SCR-STU-007",
      ui_action_id: "UIA-SHP-002",
      client_occurred_at: now(),
      product_id: productId,
      quantity,
      expected_product_version: expectedProductVersion
    })
  });
  return {
    orderId: purchase.order_id,
    status: purchase.status,
    totalAmount: purchase.total_amount,
    stateVersion: purchase.state_version
  } satisfies StorePurchaseResponse;
}

export async function fetchFinancialProducts(classroomId: string) {
  if (MOCK_FEATURES) {
    return [
      {
        resourceId: ids.depositProduct,
        label: "3-day deposit",
        status: "PUBLISHED",
        version: 1,
        stateVersion: 1,
        productType: "DEPOSIT",
        termUnits: 3,
        interestRateBps: 1_000,
        earlyTerminationRateBps: 0,
        minimumAmount: 100,
        maximumAmount: 1_000
      }
    ] satisfies FinancialProductView[];
  }

  const response = await apiRequest<{ financial_products: RawFinancialProductView[] }>(
    `/classrooms/${classroomId}/financial-products`
  );
  return response.financial_products.map((product) => ({
    resourceId: product.resource_id,
    label: product.label,
    status: product.status,
    version: product.version,
    stateVersion: product.state_version,
    productType: product.product_type,
    termUnits: product.term_units,
    interestRateBps: product.interest_rate_bps,
    earlyTerminationRateBps: product.early_termination_rate_bps,
    minimumAmount: product.minimum_amount,
    maximumAmount: product.maximum_amount
  }));
}

export async function subscribeDeposit(
  classroomId: string,
  productId: string,
  principal: number,
  expectedProductVersion: number
) {
  if (MOCK_FEATURES) {
    return {
      financialResourceId: createUuid(),
      status: "ACTIVE",
      stateVersion: 1
    } satisfies FinancialResourceResponse;
  }

  const response = await apiRequest<{
    financial_resource_id: string;
    status: string;
    state_version: number;
  }>(`/classrooms/${classroomId}/deposit-accounts`, {
    method: "POST",
    body: JSON.stringify({
      screen_id: "SCR-STU-014",
      ui_action_id: "UIA-FIN-002",
      client_occurred_at: now(),
      product_id: productId,
      principal,
      expected_product_version: expectedProductVersion
    })
  });
  return {
    financialResourceId: response.financial_resource_id,
    status: response.status,
    stateVersion: response.state_version
  } satisfies FinancialResourceResponse;
}

export async function fetchDepositAndSavings(classroomId: string) {
  if (MOCK_FEATURES) {
    return {
      depositAccounts: [
        {
          resourceId: "mock-deposit-1",
          label: "3일 예금",
          status: "ACTIVE",
          version: 1,
          stateVersion: 1,
          principalAmount: 100,
          expectedInterestAmount: 10,
          maturityAmount: 110,
          openedAt: now(),
          maturesAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString(),
          closedAt: null
        }
      ],
      savingsAccounts: []
    } satisfies DepositAndSavingsResponse;
  }

  const response = await apiRequest<{
    deposit_accounts: RawDepositAccountView[];
    savings_accounts: unknown[];
  }>(`/classrooms/${classroomId}/students/me/deposit-and-savings`);
  return {
    depositAccounts: response.deposit_accounts.map((account) => ({
      resourceId: account.resource_id,
      label: account.label,
      status: account.status,
      version: account.version,
      stateVersion: account.state_version,
      principalAmount: account.principal_amount,
      expectedInterestAmount: account.expected_interest_amount,
      maturityAmount: account.maturity_amount,
      openedAt: account.opened_at,
      maturesAt: account.matures_at,
      closedAt: account.closed_at
    })),
    savingsAccounts: response.savings_accounts
  } satisfies DepositAndSavingsResponse;
}

export async function createTrade(classroomId: string, description: string, amount: number) {
  if (MOCK_FEATURES) {
    return {
      tradeId: ids.trade,
      status: "PENDING_COUNTERPART",
      stateVersion: 1
    } satisfies TradeCommandResponse;
  }

  return apiRequest<TradeCommandResponse>(`/classrooms/${classroomId}/trades`, {
    method: "POST",
    body: JSON.stringify({
      expectedStateVersion: 0,
      screenId: "SCR-STU-TRADE",
      uiActionId: "CREATE_TRADE",
      clientOccurredAt: now(),
      counterpartStudentId: ids.counterpart,
      offeredAmount: amount,
      requestedAmount: 0,
      offeredItemIds: [],
      requestedItemIds: [],
      description
    })
  });
}

export async function cancelTrade(classroomId: string, tradeId = ids.trade) {
  if (MOCK_FEATURES) {
    return {
      tradeId,
      status: "CANCELED",
      stateVersion: 2
    } satisfies TradeCommandResponse;
  }

  return apiRequest<TradeCommandResponse>(`/classrooms/${classroomId}/trades/${tradeId}/proposer-commands`, {
    method: "POST",
    body: JSON.stringify({
      expectedStateVersion: 1,
      screenId: "SCR-STU-TRADE",
      uiActionId: "CANCEL_TRADE",
      clientOccurredAt: now(),
      command: "CANCEL"
    })
  });
}

export async function fetchQuizQuestions(classroomId: string) {
  if (MOCK_FEATURES) {
    return {
      questions: [
        {
          resourceId: ids.quizQuestion,
          label: "소득세",
          status: "ACTIVE",
          version: 1,
          stateVersion: 1,
          contentType: "TEXT",
          questionType: "MULTIPLE_CHOICE",
          prompt: "월급에서 낸 소득세는 어디로 모일까요?"
        }
      ]
    };
  }

  return apiRequest<{ questions: QuizQuestion[] }>(`/classrooms/${classroomId}/quiz-questions`);
}

export async function fetchQuizSession(classroomId: string, sessionId = ids.quizSession) {
  if (MOCK_FEATURES) {
    return {
      quizSessionId: sessionId,
      mode: "GAME",
      status: "OPEN",
      questions: [
        {
          resourceId: ids.quizQuestion,
          label: "Q1",
          status: "OPEN",
          version: 1,
          stateVersion: 1,
          prompt: "소득세는 어디로 모이나요?",
          options: ["내 지갑", "우리 반 공동기금", "상점 주인"]
        }
      ]
    } satisfies QuizSession;
  }

  return apiRequest<QuizSession>(`/classrooms/${classroomId}/quiz-sessions/${sessionId}`);
}

export async function submitGameAnswer(classroomId: string, sessionId = ids.quizSession, questionId = ids.quizQuestion, optionId = ids.quizOption) {
  if (MOCK_FEATURES) {
    return undefined;
  }

  return apiRequest<unknown>(`/classrooms/${classroomId}/quiz-sessions/${sessionId}/game-answers`, {
    method: "POST",
    body: JSON.stringify({
      expectedStateVersion: 0,
      screenId: "SCR-STU-QUIZ",
      uiActionId: "SUBMIT_GAME_ANSWER",
      clientOccurredAt: now(),
      questionId,
      optionId,
      clientAnsweredAt: now()
    })
  });
}

export async function fetchVote(classroomId: string, voteId = ids.vote) {
  if (MOCK_FEATURES) {
    return {
      voteId,
      title: "공동기금 사용처 투표",
      description: "우리 반 공동기금을 어디에 쓸지 골라요.",
      anonymous: true,
      status: "OPEN",
      stateVersion: 1,
      opensAt: now(),
      closesAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
      publicFundBalance: 340,
      participantCount: 24,
      ballotCount: 8,
      eligible: true,
      hasVoted: false,
      selectedOptionId: null,
      selectedUse: null,
      transactionGroupId: null,
      options: [
        {
          optionId: ids.voteOption,
          label: "학급 간식",
          description: "다 같이 먹을 간식을 사요.",
          budgetAmount: 180,
          voteCount: 5
        },
        {
          optionId: "00000000-0000-4000-8000-000000005003",
          label: "교실 꾸미기",
          description: "마을 게시판을 꾸며요.",
          budgetAmount: 120,
          voteCount: 3
        }
      ]
    } satisfies VoteView;
  }

  return apiRequest<VoteView>(`/classrooms/${classroomId}/votes/${voteId}`);
}

export async function castVote(classroomId: string, voteId = ids.vote, optionId = ids.voteOption) {
  if (MOCK_FEATURES) {
    return {
      receiptHash: createUuid(),
      castAt: now()
    };
  }

  return apiRequest<{ receiptHash: string; castAt: string }>(`/classrooms/${classroomId}/votes/${voteId}/ballots`, {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: 1,
      screen_id: "SCR-STU-VOTE",
      ui_action_id: "CAST_BALLOT",
      client_occurred_at: now(),
      option_id: optionId
    })
  });
}

export async function fetchGuidedActivity(classroomId: string, activityId = ids.guidedActivity) {
  if (MOCK_FEATURES) {
    return {
      activityId,
      status: "WAITING",
      participantCount: 24
    } satisfies GuidedActivity;
  }

  return apiRequest<GuidedActivity>(`/classrooms/${classroomId}/guided-activities/${activityId}`);
}
