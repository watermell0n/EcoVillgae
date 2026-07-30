export type StudentMode = "AUTONOMOUS" | "GUIDED" | "CLOSED" | "READ_ONLY";

export type ModuleCardState = "active" | "locked" | "new" | "construction" | "resultOnly";

export type ModuleCard = {
  id: string;
  title: string;
  subtitle: string;
  icon: string;
  tone: "orange" | "green" | "purple" | "red" | "yellow" | "sky";
  state: ModuleCardState;
  cta: string;
};

export type StudentScreenEnvelope<TData> = {
  screenId: string;
  role: "STU";
  mode: StudentMode;
  classroom: {
    id: string;
    name: string;
    dayIndex: number;
    status: "ACTIVE" | "CLOSED" | "READ_ONLY";
    currencyName: string;
  };
  studentView: {
    nickname: string;
    walletBalance: number;
  };
  permissions: {
    canView: boolean;
    availableActions: string[];
    blockedReason: string | null;
  };
  uiState: string;
  lastSyncedAt: string;
  data: TData;
};
