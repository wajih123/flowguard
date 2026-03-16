import { useEffect, useRef, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import * as Keychain from 'react-native-keychain';
import { useAlertStore } from '../store/alertStore';

const WS_BASE = process.env.EXPO_PUBLIC_WS_URL ?? 'ws://10.0.2.2:8080';
const PING_INTERVAL_MS = 25_000;
const MAX_BACKOFF_MS = 60_000;

type WSMessage =
  | { type: 'CONNECTED' }
  | { type: 'UNREAD_COUNT'; count: number }
  | { type: 'NEW_ALERT'; alertId: string }
  | { type: 'pong' }

export const useWebSocket = (accountId: string | undefined) => {
  const queryClient = useQueryClient();
  const setUnreadCount = useAlertStore((s) => s.setUnreadCount);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const attemptsRef = useRef(0);
  const mountedRef = useRef(true);

  const clearTimers = useCallback(() => {
    if (reconnectTimerRef.current) {clearTimeout(reconnectTimerRef.current);}
    if (pingTimerRef.current) {clearInterval(pingTimerRef.current);}
    reconnectTimerRef.current = null;
    pingTimerRef.current = null;
  }, []);

  const getBackoffMs = useCallback((attempt: number) => {
    const base = 3_000 * Math.pow(2, attempt);
    return Math.min(base, MAX_BACKOFF_MS);
  }, []);

  const connect = useCallback(async () => {
    if (!accountId || !mountedRef.current) {return;}

    const creds = await Keychain.getGenericPassword({ service: 'fg' }).catch(() => null);
    if (!creds) {return;}

    let accessToken: string;
    try {
      const parsed = JSON.parse(creds.password) as { accessToken?: string };
      accessToken = parsed.accessToken ?? creds.password;
    } catch {
      accessToken = creds.password;
    }

    const url = `${WS_BASE}/ws/alerts?accountId=${accountId}&token=${encodeURIComponent(accessToken)}`;
    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {
      attemptsRef.current = 0;
      pingTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {ws.send(JSON.stringify({ type: 'ping' }));}
      }, PING_INTERVAL_MS);
    };

    ws.onmessage = (event) => {
      if (!mountedRef.current) {return;}
      let msg: WSMessage;
      try {
        msg = JSON.parse(event.data as string) as WSMessage;
      } catch {
        return;
      }
      if (msg.type === 'UNREAD_COUNT') {
        setUnreadCount(msg.count);
      } else if (msg.type === 'NEW_ALERT') {
        queryClient.invalidateQueries({ queryKey: ['alerts', accountId] });
      }
    };

    ws.onerror = () => {
      /* handled in onclose */
    };

    ws.onclose = () => {
      clearTimers();
      if (!mountedRef.current) {return;}
      const delay = getBackoffMs(attemptsRef.current);
      attemptsRef.current += 1;
      reconnectTimerRef.current = setTimeout(() => void connect(), delay);
    };
  }, [accountId, queryClient, setUnreadCount, clearTimers, getBackoffMs]);

  useEffect(() => {
    mountedRef.current = true;
    void connect();
    return () => {
      mountedRef.current = false;
      clearTimers();
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [connect, clearTimers]);
};
