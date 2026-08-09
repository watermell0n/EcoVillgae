import { randomUUID } from "expo-crypto";

import { apiRequest, getRealtimeWebSocketUrl } from "@/api/client";

export type StudentRealtimeEvent = {
  type: "domain.event";
  event_id: string;
  event_type: string;
  channel_id: string;
  subscription_id: string;
  sequence: number;
  classroom_id: string;
  resource_id: string | null;
  state_version: number;
  payload: Record<string, unknown>;
};

type RealtimeMessage = Record<string, unknown> & { type: string };

type StudentEconomyRealtimeOptions = {
  classroomId: string;
  onEvent: (event: StudentRealtimeEvent) => void;
  onRecoveryRequired: () => void;
};

type RealtimeTicketResponse = {
  ticket: string;
  expires_at: string;
};

const ECONOMY_CHANNEL_ID = "WS-ECO-001";
const INVESTMENT_CHANNEL_ID = "WS-IVT-001";
const CHANNEL_IDS = [ECONOMY_CHANNEL_ID, INVESTMENT_CHANNEL_ID] as const;
const MAX_RECONNECT_DELAY_MS = 15_000;

async function createRealtimeTicket(classroomId: string) {
  return apiRequest<RealtimeTicketResponse>("/auth/realtime-tickets", {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: 0,
      screen_id: "SCR-STU-001",
      ui_action_id: "UIA-STU-001",
      client_occurred_at: new Date().toISOString(),
      channel_ids: [...CHANNEL_IDS],
      resource_ids: [classroomId]
    })
  });
}

/**
 * 학생 은행 화면이 교사의 금융 상품 공개·수정 이벤트를 즉시 받기 위한 최소 실시간 구독입니다.
 * 서버의 ECONOMY 채널을 그대로 사용하고, 연결 복구가 필요하면 화면 쿼리를 다시 읽게 합니다.
 */
export class StudentEconomyRealtimeClient {
  private readonly options: StudentEconomyRealtimeOptions;
  private readonly seenEventIds = new Set<string>();
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectAttempts = 0;
  private readonly lastSequenceByChannel = new Map<string, number>();
  private stopped = true;
  private presenceStatus: "ONLINE" | "AWAY" = "ONLINE";
  private currentScreen: string | null = null;

  constructor(options: StudentEconomyRealtimeOptions) {
    this.options = options;
  }

  start() {
    if (!this.stopped) return;
    this.stopped = false;
    void this.connect();
  }

  stop() {
    this.stopped = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
    this.reconnectTimer = null;
    this.heartbeatTimer = null;
    this.socket?.close(1000, "client stopped");
    this.socket = null;
  }

  updatePresence(status: "ONLINE" | "AWAY", currentScreen: string | null) {
    this.presenceStatus = status;
    this.currentScreen = currentScreen;
    this.sendPresence();
  }

  private async connect() {
    try {
      const issued = await createRealtimeTicket(this.options.classroomId);
      if (this.stopped) return;
      const url = new URL(getRealtimeWebSocketUrl());
      url.searchParams.set("ticket", issued.ticket);
      const socket = new WebSocket(url.toString());
      this.socket = socket;
      socket.onmessage = (message) => this.handleMessage(message.data);
      socket.onclose = () => {
        if (this.socket === socket) this.socket = null;
        this.clearHeartbeat();
        this.scheduleReconnect();
      };
    } catch {
      this.scheduleReconnect();
    }
  }

  private handleMessage(raw: unknown) {
    if (typeof raw !== "string") return;
    let message: RealtimeMessage;
    try {
      message = JSON.parse(raw) as RealtimeMessage;
    } catch {
      return;
    }

    if (message.type === "connection.ready") {
      this.reconnectAttempts = 0;
      this.startHeartbeat(Number(message.heartbeat_interval_seconds ?? 25));
      this.sendPresence();
      CHANNEL_IDS.forEach((channelId) => this.send({
        type: "subscription.subscribe",
        request_id: randomUUID(),
        channel_id: channelId,
        resource_id: this.options.classroomId,
        last_sequence: this.lastSequenceByChannel.get(channelId) ?? 0
      }));
      return;
    }

    if (message.type === "domain.event") {
      const event = message as StudentRealtimeEvent;
      if (this.seenEventIds.has(event.event_id)) return;
      this.seenEventIds.add(event.event_id);
      if (this.seenEventIds.size > 500) {
        const oldest = this.seenEventIds.values().next().value;
        if (oldest) this.seenEventIds.delete(oldest);
      }
      this.lastSequenceByChannel.set(
        event.channel_id,
        Math.max(this.lastSequenceByChannel.get(event.channel_id) ?? 0, event.sequence)
      );
      this.send({
        type: "event.ack",
        subscription_id: event.subscription_id,
        sequence: event.sequence
      });
      this.options.onEvent(event);
      return;
    }

    if (message.type === "recovery.required") {
      const channelId = typeof message.channel_id === "string" ? message.channel_id : "";
      if (channelId) {
        this.lastSequenceByChannel.set(channelId, Number(message.current_sequence ?? 0));
      }
      this.options.onRecoveryRequired();
    }
  }

  private startHeartbeat(seconds: number) {
    this.clearHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: "connection.ping", client_time: new Date().toISOString() });
    }, Math.max(5, seconds) * 1_000);
  }

  private clearHeartbeat() {
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = null;
  }

  private scheduleReconnect() {
    if (this.stopped || this.reconnectTimer) return;
    const delay = Math.min(1_000 * 2 ** this.reconnectAttempts++, MAX_RECONNECT_DELAY_MS);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      void this.connect();
    }, delay);
  }

  private send(message: Record<string, unknown>) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    }
  }

  private sendPresence() {
    this.send({
      type: "presence.update",
      status: this.presenceStatus,
      current_screen: this.currentScreen
    });
  }
}
