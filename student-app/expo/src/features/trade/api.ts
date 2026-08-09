import { apiRequest } from "@/api/client";

const useMocks = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true";

export type TradeClassmate = {
  studentId: string;
  rosterNumber: number;
  nickname: string;
};

export type StudentTrade = {
  id: string;
  proposerStudentId: string;
  proposerNickname: string;
  counterpartStudentId: string;
  counterpartNickname: string;
  description: string;
  amount: number;
  status: string;
  stateVersion: number;
  createdAt: string;
};

type TradeResponse = {
  trade_id: string;
  proposer_student_id: string;
  proposer_nickname?: string;
  counterpart_student_id: string;
  counterpart_nickname?: string;
  description?: string | null;
  amount?: number;
  status: string;
  state_version: number;
  created_at: string;
};

export async function fetchTradeClassmates(classroomId: string): Promise<TradeClassmate[]> {
  if (useMocks) return [
    { studentId: "00000000-0000-0000-0000-000000000002", rosterNumber: 2, nickname: "박서윤" },
    { studentId: "00000000-0000-0000-0000-000000000003", rosterNumber: 3, nickname: "이도윤" }
  ];
  const response = await apiRequest<Array<{
    student_id: string;
    roster_number: number;
    nickname: string;
  }>>(`/classrooms/${classroomId}/trades/classmates`);
  return response.map((student) => ({
    studentId: student.student_id,
    rosterNumber: student.roster_number,
    nickname: student.nickname
  }));
}

export async function fetchMyTrades(classroomId: string): Promise<StudentTrade[]> {
  if (useMocks) return [];
  const response = await apiRequest<TradeResponse[]>(`/classrooms/${classroomId}/trades/mine`);
  return response.map((trade) => ({
    id: trade.trade_id,
    proposerStudentId: trade.proposer_student_id,
    proposerNickname: trade.proposer_nickname ?? "학생",
    counterpartStudentId: trade.counterpart_student_id,
    counterpartNickname: trade.counterpart_nickname ?? "학생",
    description: trade.description ?? "",
    amount: trade.amount ?? 0,
    status: trade.status,
    stateVersion: trade.state_version,
    createdAt: trade.created_at
  }));
}

export async function decideTrade(
  classroomId: string,
  tradeId: string,
  stateVersion: number,
  decision: "ACCEPT" | "REJECT"
) {
  if (useMocks) return;
  await apiRequest(`/classrooms/${classroomId}/trades/${tradeId}/counterpart-decisions`, {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: stateVersion,
      screen_id: "SCR-STU-010",
      ui_action_id: decision === "ACCEPT" ? "UIA-TRD-002" : "UIA-TRD-003",
      client_occurred_at: new Date().toISOString(),
      decision,
      reason: decision === "ACCEPT" ? "거래 내용에 동의했습니다." : "거래를 거절했습니다."
    })
  });
}
