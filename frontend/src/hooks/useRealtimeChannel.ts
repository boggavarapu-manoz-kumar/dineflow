import { useEffect, useRef, useState, useCallback } from 'react';
import { getAccessToken } from '@/lib/authClient';
import {
  buildStompFrame,
  ConnectionState,
  parseStompMessageFrame,
  RealtimeEventEnvelope,
  RealtimeEventGuard,
  resolveWebSocketUrl,
} from '@/lib/realtimeClient';

interface UseRealtimeChannelOptions {
  destinations: string[];
  enabled: boolean;
  onEvent: (event: RealtimeEventEnvelope) => void;
  onReconnect?: () => void;
}

/**
 * React hook for authenticated STOMP over WebSocket subscriptions with:
 * - JWT Bearer handshake
 * - Deduplication & out-of-order protection via RealtimeEventGuard
 * - Exponential-backoff auto-reconnection
 * - Authoritative REST refresh callback on every reconnect
 */
export function useRealtimeChannel({
  destinations,
  enabled,
  onEvent,
  onReconnect,
}: UseRealtimeChannelOptions) {
  const [connectionState, setConnectionState] = useState<ConnectionState>(
    enabled ? 'CONNECTING' : 'DISCONNECTED'
  );
  const [lastEventAt, setLastEventAt] = useState<number | null>(null);

  const guardRef = useRef<RealtimeEventGuard>(new RealtimeEventGuard());
  const onEventRef = useRef(onEvent);
  const onReconnectRef = useRef(onReconnect);
  const hadConnectedOnceRef = useRef(false);
  const retryAttemptRef = useRef(0);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  onEventRef.current = onEvent;
  onReconnectRef.current = onReconnect;

  const destinationKey = destinations.filter(Boolean).sort().join('|');

  const cleanupSocket = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    if (wsRef.current) {
      const socket = wsRef.current;
      wsRef.current = null;
      try {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(buildStompFrame('DISCONNECT'));
        }
        socket.close();
      } catch {
        // Ignore close errors during teardown
      }
    }
  }, []);

  useEffect(() => {
    if (!enabled || !destinationKey || typeof WebSocket === 'undefined') {
      setConnectionState('DISCONNECTED');
      return;
    }

    const token = getAccessToken();
    if (!token) {
      setConnectionState('DISCONNECTED');
      return;
    }

    let unmounted = false;
    const targetDestinations = destinationKey.split('|').filter(Boolean);

    const connect = () => {
      if (unmounted) return;
      setConnectionState(hadConnectedOnceRef.current ? 'RECONNECTING' : 'CONNECTING');

      try {
        const ws = new WebSocket(resolveWebSocketUrl());
        wsRef.current = ws;

        ws.onopen = () => {
          if (unmounted) return;
          ws.send(
            buildStompFrame('CONNECT', {
              'accept-version': '1.2',
              'heart-beat': '0,10000',
              Authorization: `Bearer ${getAccessToken() || token}`,
            })
          );
        };

        ws.onmessage = (msg) => {
          if (unmounted || typeof msg.data !== 'string') return;
          const frame = parseStompMessageFrame(msg.data);
          if (!frame) return;

          if (frame.command === 'CONNECTED') {
            const isReconnect = hadConnectedOnceRef.current;
            hadConnectedOnceRef.current = true;
            retryAttemptRef.current = 0;
            setConnectionState('CONNECTED');

            targetDestinations.forEach((dest, idx) => {
              ws.send(
                buildStompFrame('SUBSCRIBE', {
                  id: `sub-${idx}`,
                  destination: dest,
                })
              );
            });

            if (isReconnect) {
              guardRef.current.resetResourceTracking();
              onReconnectRef.current?.();
            }
          } else if (frame.command === 'MESSAGE' && frame.body) {
            try {
              const parsed = JSON.parse(frame.body) as RealtimeEventEnvelope;
              if (guardRef.current.accept(parsed)) {
                setLastEventAt(Date.now());
                onEventRef.current(parsed);
              }
            } catch {
              // Ignore malformed JSON frames
            }
          }
        };

        ws.onerror = () => {
          // onclose handles backoff scheduling
        };

        ws.onclose = () => {
          if (unmounted) return;
          setConnectionState(hadConnectedOnceRef.current ? 'RECONNECTING' : 'DISCONNECTED');
          const delayMs = Math.min(15000, 1000 * Math.pow(2, retryAttemptRef.current));
          retryAttemptRef.current = Math.min(retryAttemptRef.current + 1, 5);
          reconnectTimerRef.current = setTimeout(connect, delayMs);
        };
      } catch {
        setConnectionState('DISCONNECTED');
      }
    };

    connect();

    return () => {
      unmounted = true;
      cleanupSocket();
    };
  }, [enabled, destinationKey, cleanupSocket]);

  return {
    connectionState,
    lastEventAt,
  };
}
