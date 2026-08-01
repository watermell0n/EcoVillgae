import { apiRequest } from "./client";

const MOCK_FEATURES = import.meta.env.VITE_USE_MOCK_STUDENT_FEATURES !== "false";

export type StorePurchaseResponse = {
  orderId: string;
  status: string;
  totalAmount: number;
  stateVersion: number;
};

export type DepositAccountView = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  stateVersion: number;
  principalAmount: number;
  maturesAt: string;
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

export async function purchaseStoreItem(
  classroomId: string,
  productId = ids.product,
  quantity = 1,
  expectedProductVersion = 1
) {
  if (MOCK_FEATURES) {
    return {
      orderId: crypto.randomUUID(),
      status: "CREATED",
      totalAmount: 50 * quantity,
      stateVersion: 1
    } satisfies StorePurchaseResponse;
  }

  return apiRequest<StorePurchaseResponse>(`/classrooms/${classroomId}/store/orders`, {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: 0,
      screen_id: "SCR-STU-SHOP",
      ui_action_id: "BUY_PRODUCT",
      client_occurred_at: now(),
      product_id: productId,
      quantity,
      expected_product_version: expectedProductVersion
    })
  });
}

export async function subscribeDeposit(classroomId: string, productId = ids.depositProduct, principal = 100) {
  if (MOCK_FEATURES) {
    return {
      financialResourceId: crypto.randomUUID(),
      status: "ACTIVE",
      stateVersion: 1
    } satisfies FinancialResourceResponse;
  }

  return apiRequest<FinancialResourceResponse>(`/classrooms/${classroomId}/deposit-accounts`, {
    method: "POST",
    body: JSON.stringify({
      expectedStateVersion: 0,
      screenId: "SCR-STU-BANK",
      uiActionId: "SUBSCRIBE_DEPOSIT",
      clientOccurredAt: now(),
      productId,
      principal,
      expectedProductVersion: 1
    })
  });
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
          maturesAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString()
        }
      ],
      savingsAccounts: []
    } satisfies DepositAndSavingsResponse;
  }

  return apiRequest<DepositAndSavingsResponse>(`/classrooms/${classroomId}/students/me/deposit-and-savings`);
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
      receiptHash: crypto.randomUUID(),
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
