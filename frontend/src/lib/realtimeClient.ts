export type ConnectionState = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED';

export interface RealtimeEventEnvelope<T = Record<string, unknown>> {
  eventId: string;
  eventType: string;
  eventVersion: string;
  tenantId: string;
  branchId?: string | null;
  channel: string;
  resourceId?: string | null;
  domainVersion?: number | null;
  payload: T;
  timestamp: string;
}

const ORDER_STATUS_RANK: Record<string, number> = {
  PLACED: 1,
  ACCEPTED: 2,
  PREPARING: 3,
  READY: 4,
  SERVED: 5,
  PAID: 6,
  CANCELLED: 7,
};

/**
 * Pure, testable deduplication & out-of-order guard for at-least-once RealtimeEvent streams.
 */
export class RealtimeEventGuard {
  private readonly seenEventIds = new Set<string>();
  private readonly eventOrderQueue: string[] = [];
  private readonly highestDomainVersion = new Map<string, number>();
  private readonly highestStatusRank = new Map<string, number>();
  private readonly maxSeenSize: number;

  constructor(maxSeenSize = 500) {
    this.maxSeenSize = maxSeenSize;
  }

  /**
   * Returns true if the event should be accepted; false if duplicate or stale/out-of-order.
   */
  accept(event: RealtimeEventEnvelope): boolean {
    if (!event || !event.eventId) {
      return false;
    }
    if (this.seenEventIds.has(event.eventId)) {
      return false;
    }

    const resourceKey = event.resourceId ? `${event.channel}:${event.resourceId}` : null;

    if (resourceKey && typeof event.domainVersion === 'number') {
      const prevVersion = this.highestDomainVersion.get(resourceKey);
      if (prevVersion !== undefined && event.domainVersion < prevVersion) {
        return false; // Stale out-of-order event
      }
      this.highestDomainVersion.set(resourceKey, event.domainVersion);
    }

    const nextStatus = typeof event.payload?.status === 'string' ? event.payload.status : null;
    if (resourceKey && nextStatus && ORDER_STATUS_RANK[nextStatus] !== undefined) {
      const prevRank = this.highestStatusRank.get(resourceKey);
      const nextRank = ORDER_STATUS_RANK[nextStatus];
      if (prevRank !== undefined && nextRank < prevRank) {
        return false; // Never move an order backward due to out-of-order delivery
      }
      this.highestStatusRank.set(resourceKey, nextRank);
    }

    this.seenEventIds.add(event.eventId);
    this.eventOrderQueue.push(event.eventId);
    if (this.eventOrderQueue.length > this.maxSeenSize) {
      const oldest = this.eventOrderQueue.shift();
      if (oldest) this.seenEventIds.delete(oldest);
    }
    return true;
  }

  resetResourceTracking(): void {
    this.highestDomainVersion.clear();
    this.highestStatusRank.clear();
  }
}

export function buildStompFrame(command: string, headers: Record<string, string> = {}, body = ''): string {
  const headerLines = Object.entries(headers).map(([k, v]) => `${k}:${v}`);
  return `${command}\n${headerLines.join('\n')}\n\n${body}\0`;
}

export function parseStompMessageFrame(rawData: string): { command: string; headers: Record<string, string>; body: string } | null {
  const trimmed = rawData.replace(/\0+$/, '');
  if (!trimmed || trimmed === '\n' || trimmed === '\r\n') {
    return null; // Heartbeat frame
  }
  const splitIdx = trimmed.indexOf('\n\n');
  const headerSection = splitIdx >= 0 ? trimmed.slice(0, splitIdx) : trimmed;
  const body = splitIdx >= 0 ? trimmed.slice(splitIdx + 2) : '';
  const lines = headerSection.split(/\r?\n/);
  const command = lines[0]?.trim() || '';
  const headers: Record<string, string> = {};
  for (let i = 1; i < lines.length; i++) {
    const colonIdx = lines[i].indexOf(':');
    if (colonIdx > 0) {
      headers[lines[i].slice(0, colonIdx).trim()] = lines[i].slice(colonIdx + 1).trim();
    }
  }
  return { command, headers, body };
}

export function resolveWebSocketUrl(): string {
  const explicitWs = import.meta.env.VITE_WS_BASE_URL as string | undefined;
  if (explicitWs) {
    return explicitWs;
  }
  const apiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined) || 'http://localhost:8080/api/v1';
  if (apiBase.startsWith('/')) {
    const protocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = typeof window !== 'undefined' ? window.location.host : 'localhost:8080';
    return `${protocol}//${host}/ws`;
  }
  const httpOrigin = apiBase.replace(/\/api\/v1\/?$/, '');
  return httpOrigin.replace(/^http/, 'ws') + '/ws';
}
