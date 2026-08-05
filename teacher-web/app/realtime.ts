import { createRealtimeTicket, getRealtimeWebSocketUrl } from "./api";

export type RealtimeSubscriptionRequest = {
  channelId: string;
  resourceId: string;
};

export type RealtimeDomainEvent = {
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

export type StudentPresenceChange = {
  type: "presence.changed";
  classroom_id: string;
  student_id: string;
  status: "ONLINE" | "AWAY" | "OFFLINE" | "UNKNOWN";
  current_screen: string | null;
  last_seen_at: string | null;
};

type RealtimeMessage = Record<string, unknown> & { type: string };

type RealtimeClientOptions = {
  subscriptions: RealtimeSubscriptionRequest[];
  onEvent: (event: RealtimeDomainEvent) => void;
  onPresenceChange?: (presence: StudentPresenceChange) => void;
  onRecoveryRequired?: () => void;
  onError?: (error: Error) => void;
};

const MAX_RECONNECT_DELAY_MS = 15_000;
const SEQUENCE_PREFIX = "ecovillage.teacher.realtime.sequence";

export class TeacherRealtimeClient {
  private readonly options: RealtimeClientOptions;
  private readonly subscriptionKeys = new Map<string, string>();
  private readonly seenEventIds = new Set<string>();
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectAttempts = 0;
  private stopped = true;

  constructor(options: RealtimeClientOptions) {
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
    this.subscriptionKeys.clear();
  }

  private async connect() {
    try {
      const channelIds = [...new Set(this.options.subscriptions.map((item) => item.channelId))];
      const resourceIds = [...new Set(this.options.subscriptions.map((item) => item.resourceId))];
      const issued = await createRealtimeTicket(channelIds, resourceIds);
      if (this.stopped) return;

      const url = new URL(getRealtimeWebSocketUrl());
      url.searchParams.set("ticket", issued.ticket);
      const socket = new WebSocket(url);
      this.socket = socket;
      socket.onmessage = (message) => this.handleMessage(message.data);
      socket.onerror = () => this.options.onError?.(new Error("실시간 연결에 오류가 발생했습니다."));
      socket.onclose = () => {
        if (this.socket === socket) this.socket = null;
        this.clearHeartbeat();
        this.scheduleReconnect();
      };
    } catch (error) {
      this.options.onError?.(error instanceof Error ? error : new Error("실시간 티켓 발급에 실패했습니다."));
      this.scheduleReconnect();
    }
  }

  private handleMessage(raw: unknown) {
    if (typeof raw !== "string") return;
    let message: RealtimeMessage;
    try {
      message = JSON.parse(raw) as RealtimeMessage;
    } catch {
      this.options.onError?.(new Error("잘못된 실시간 메시지를 수신했습니다."));
      return;
    }
    if (message.type === "connection.ready") {
      this.reconnectAttempts = 0;
      const heartbeatSeconds = Number(message.heartbeat_interval_seconds ?? 25);
      this.startHeartbeat(heartbeatSeconds);
      this.options.subscriptions.forEach((subscription) => this.subscribe(subscription));
      return;
    }
    if (message.type === "subscription.accepted") {
      const subscriptionId = String(message.subscription_id);
      this.subscriptionKeys.set(
        subscriptionId,
        this.sequenceKey(String(message.channel_id), String(message.resource_id))
      );
      return;
    }
    if (message.type === "domain.event") {
      this.handleEvent(message as RealtimeDomainEvent);
      return;
    }
    if (message.type === "presence.changed") {
      this.options.onPresenceChange?.(message as StudentPresenceChange);
      return;
    }
    if (message.type === "recovery.required") {
      const key = this.subscriptionKeys.get(String(message.subscription_id));
      if (key) this.storeSequence(key, Number(message.current_sequence));
      this.options.onRecoveryRequired?.();
      return;
    }
    if (message.type === "protocol.error") {
      this.options.onError?.(new Error(String(message.message ?? "실시간 프로토콜 오류가 발생했습니다.")));
    }
  }

  private subscribe(subscription: RealtimeSubscriptionRequest) {
    const key = this.sequenceKey(subscription.channelId, subscription.resourceId);
    this.send({
      type: "subscription.subscribe",
      request_id: crypto.randomUUID(),
      channel_id: subscription.channelId,
      resource_id: subscription.resourceId,
      last_sequence: this.loadSequence(key)
    });
  }

  private handleEvent(message: RealtimeDomainEvent) {
    if (this.seenEventIds.has(message.event_id)) return;
    this.seenEventIds.add(message.event_id);
    if (this.seenEventIds.size > 500) {
      const oldest = this.seenEventIds.values().next().value;
      if (oldest) this.seenEventIds.delete(oldest);
    }
    const key = this.subscriptionKeys.get(message.subscription_id);
    if (key) this.storeSequence(key, message.sequence);
    this.send({
      type: "event.ack",
      subscription_id: message.subscription_id,
      sequence: message.sequence
    });
    this.options.onEvent(message);
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

  private sequenceKey(channelId: string, resourceId: string) {
    return `${SEQUENCE_PREFIX}:${channelId}:${resourceId}`;
  }

  private loadSequence(key: string) {
    try {
      return Math.max(0, Number(sessionStorage.getItem(key) ?? 0));
    } catch {
      return 0;
    }
  }

  private storeSequence(key: string, sequence: number) {
    try {
      sessionStorage.setItem(key, String(sequence));
    } catch {
      // Storage can be unavailable in private browsing; the live connection still works.
    }
  }
}
