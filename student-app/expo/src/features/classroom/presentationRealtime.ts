import { randomUUID } from "expo-crypto";

import { apiRequest, getRealtimeWebSocketUrl } from "@/api/client";

const ACTIVITY_CHANNEL_ID = "WS-ACT-001";
const MAX_RECONNECT_DELAY_MS = 15_000;

type PresentationRealtimeOptions = {
  classroomId: string;
  /** 진행 중인 선생님 수업의 활동 id입니다. 수업마다 바뀌므로 티켓도 새로 받습니다. */
  activityId: string;
  /** 자료가 넘어갔을 때 호출합니다. 이벤트에는 자료가 없으므로 현재 장을 다시 읽어야 합니다. */
  onPresentationChanged: () => void;
};

type RealtimeTicketResponse = {
  ticket: string;
  expires_at: string;
};

/**
 * 선생님이 자료를 넘기면 학생 화면이 곧바로 따라오게 하는 구독입니다.
 *
 * <p>활동 채널(WS-ACT-001)은 리소스 범위라 티켓에 활동 id가 들어가야 합니다. 그래서 수업이
 * 바뀔 때마다 이 클라이언트를 새로 만듭니다. 경제 채널 구독(StudentEconomyRealtimeClient)과
 * 수명이 달라 한 연결에 묶지 않고 따로 둡니다.
 *
 * <p>이벤트는 "다시 읽어라"는 신호로만 씁니다. 자료 이미지는 수백 KB라 이벤트로 나르지
 * 않습니다. 연결이 끊겨도 화면 쿼리의 폴링이 남아 있어 수업이 멈추지는 않습니다.
 */
export class PresentationRealtimeClient {
  private readonly options: PresentationRealtimeOptions;
  private readonly seenEventIds = new Set<string>();
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectAttempts = 0;
  private lastSequence = 0;
  private stopped = true;

  constructor(options: PresentationRealtimeOptions) {
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

  private async createTicket() {
    return apiRequest<RealtimeTicketResponse>("/auth/realtime-tickets", {
      method: "POST",
      body: JSON.stringify({
        expected_state_version: 0,
        screen_id: "SCR-STU-021",
        ui_action_id: "UIA-STU-001",
        client_occurred_at: new Date().toISOString(),
        channel_ids: [ACTIVITY_CHANNEL_ID],
        resource_ids: [this.options.activityId]
      })
    });
  }

  private async connect() {
    try {
      const issued = await this.createTicket();
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
    let message: Record<string, unknown> & { type?: string };
    try {
      message = JSON.parse(raw) as Record<string, unknown> & { type?: string };
    } catch {
      return;
    }

    if (message.type === "connection.ready") {
      this.reconnectAttempts = 0;
      this.startHeartbeat(Number(message.heartbeat_interval_seconds ?? 25));
      this.send({
        type: "subscription.subscribe",
        request_id: randomUUID(),
        channel_id: ACTIVITY_CHANNEL_ID,
        resource_id: this.options.activityId,
        last_sequence: this.lastSequence
      });
      return;
    }

    if (message.type === "domain.event") {
      const eventId = String(message.event_id ?? "");
      if (eventId && this.seenEventIds.has(eventId)) return;
      if (eventId) {
        this.seenEventIds.add(eventId);
        if (this.seenEventIds.size > 200) {
          const oldest = this.seenEventIds.values().next().value;
          if (oldest) this.seenEventIds.delete(oldest);
        }
      }
      const sequence = Number(message.sequence ?? 0);
      this.lastSequence = Math.max(this.lastSequence, sequence);
      this.send({
        type: "event.ack",
        subscription_id: message.subscription_id,
        sequence
      });
      // 자료가 넘어간 것만 화면에 반영합니다. 시작·종료는 학급 접근 쿼리가 이미 다룹니다.
      if (message.event_type === "activity.presentation.changed") {
        this.options.onPresentationChanged();
      }
      return;
    }

    if (message.type === "recovery.required") {
      this.lastSequence = Math.max(this.lastSequence, Number(message.current_sequence ?? 0));
      // 놓친 구간이 있으면 현재 장을 다시 읽어 맞춥니다.
      this.options.onPresentationChanged();
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
}
