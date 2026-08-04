import { createContext, useContext, useMemo, useState, type PropsWithChildren } from "react";

export type TradeStatus = "AWAITING_COUNTERPART" | "AWAITING_TEACHER" | "COMPLETED" | "DECLINED" | "REJECTED";
export type TradeRequest = {
  id: string;
  counterpartId: string;
  counterpartName: string;
  amount: number;
  reason: string;
  direction: "INCOMING" | "OUTGOING";
  status: TradeStatus;
  createdAt: string;
};

type TradeContextValue = {
  trades: TradeRequest[];
  pendingIncomingCount: number;
  createLocalTrade(input: Omit<TradeRequest, "direction" | "createdAt">): void;
  decideIncomingTrade(id: string, status: "AWAITING_TEACHER" | "DECLINED"): void;
};

const TradeContext = createContext<TradeContextValue | null>(null);
const useMocks = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true";

const mockTrades: TradeRequest[] = [
  { id: "mock-incoming-1", counterpartId: "mock-student-02", counterpartName: "박서윤", amount: 15, reason: "모둠 발표 자료를 함께 준비해요.", direction: "INCOMING", status: "AWAITING_COUNTERPART", createdAt: "방금 전" },
  { id: "mock-outgoing-1", counterpartId: "mock-student-01", counterpartName: "김하늘", amount: 20, reason: "학급 화분 돌봄을 함께 해요.", direction: "OUTGOING", status: "AWAITING_TEACHER", createdAt: "10분 전" }
];

export function TradeProvider({ children }: PropsWithChildren) {
  const [trades, setTrades] = useState<TradeRequest[]>(useMocks ? mockTrades : []);
  const value = useMemo<TradeContextValue>(() => ({
    trades,
    pendingIncomingCount: trades.filter((trade) => trade.direction === "INCOMING" && trade.status === "AWAITING_COUNTERPART").length,
    createLocalTrade(input) {
      setTrades((current) => [{ ...input, direction: "OUTGOING", createdAt: "방금 전" }, ...current]);
    },
    decideIncomingTrade(id, status) {
      setTrades((current) => current.map((trade) => trade.id === id ? { ...trade, status } : trade));
    }
  }), [trades]);
  return <TradeContext.Provider value={value}>{children}</TradeContext.Provider>;
}

export function useTrades() {
  const value = useContext(TradeContext);
  if (!value) throw new Error("TradeProvider is missing");
  return value;
}
