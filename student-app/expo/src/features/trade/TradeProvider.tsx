import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useMemo, type PropsWithChildren } from "react";

import { useAuth } from "@/auth/AuthProvider";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { decideTrade, fetchMyTrades } from "./api";

export type TradeStatus =
  | "AWAITING_COUNTERPART"
  | "AWAITING_TEACHER"
  | "SETTLING"
  | "COMPLETED"
  | "CANCELED"
  | "DECLINED"
  | "EXPIRED"
  | "REJECTED"
  | "FAILED";

export type TradeRequest = {
  id: string;
  counterpartId: string;
  counterpartName: string;
  amount: number;
  reason: string;
  direction: "INCOMING" | "OUTGOING";
  status: TradeStatus;
  stateVersion: number;
  createdAt: string;
};

type TradeContextValue = {
  trades: TradeRequest[];
  pendingIncomingCount: number;
  refresh(): Promise<unknown>;
  decideIncomingTrade(id: string, decision: "ACCEPT" | "REJECT"): Promise<void>;
};

const TradeContext = createContext<TradeContextValue | null>(null);

export function TradeProvider({ children }: PropsWithChildren) {
  const { identity } = useAuth();
  const { enabled } = useClassroom();
  const queryClient = useQueryClient();
  const classroomId = identity?.classroomId;
  const query = useQuery({
    queryKey: ["student-trades", classroomId],
    queryFn: () => fetchMyTrades(classroomId!),
    enabled: Boolean(classroomId && enabled("trade")),
    refetchInterval: 5_000
  });

  const trades = useMemo(() => (query.data ?? []).map<TradeRequest>((trade) => {
    const outgoing = trade.proposerStudentId === identity?.studentId;
    return {
      id: trade.id,
      counterpartId: outgoing ? trade.counterpartStudentId : trade.proposerStudentId,
      counterpartName: outgoing ? trade.counterpartNickname : trade.proposerNickname,
      amount: trade.amount,
      reason: trade.description,
      direction: outgoing ? "OUTGOING" : "INCOMING",
      status: trade.status as TradeStatus,
      stateVersion: trade.stateVersion,
      createdAt: trade.createdAt
    };
  }), [identity?.studentId, query.data]);

  const refresh = useCallback(() => query.refetch(), [query.refetch]);
  const decideIncomingTrade = useCallback(async (id: string, decision: "ACCEPT" | "REJECT") => {
    const trade = trades.find((item) => item.id === id);
    if (!classroomId || !trade) throw new Error("거래 정보를 다시 불러와 주세요.");
    await decideTrade(classroomId, id, trade.stateVersion, decision);
    await queryClient.invalidateQueries({ queryKey: ["student-trades", classroomId] });
  }, [classroomId, queryClient, trades]);

  const value = useMemo<TradeContextValue>(() => ({
    trades,
    pendingIncomingCount: trades.filter((trade) => (
      trade.direction === "INCOMING" && trade.status === "AWAITING_COUNTERPART"
    )).length,
    refresh,
    decideIncomingTrade
  }), [decideIncomingTrade, refresh, trades]);

  return <TradeContext.Provider value={value}>{children}</TradeContext.Provider>;
}

export function useTrades() {
  const value = useContext(TradeContext);
  if (!value) throw new Error("TradeProvider is missing");
  return value;
}
