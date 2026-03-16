import { create } from 'zustand';
import type { Alert } from '../domain/Alert';

interface AlertState {
  unreadCount: number
  alerts: Alert[]
  setUnreadCount: (n: number) => void
  incrementUnread: () => void
  decrementUnread: () => void
  resetUnread: () => void
  setAlerts: (alerts: Alert[]) => void
  markRead: (alertId: string) => void
  markAllRead: () => void
}

export const useAlertStore = create<AlertState>((set) => ({
  unreadCount: 0,
  alerts: [],

  setUnreadCount: (n: number) => set({ unreadCount: n }),

  incrementUnread: () => set((state) => ({ unreadCount: state.unreadCount + 1 })),

  decrementUnread: () => set((state) => ({ unreadCount: Math.max(0, state.unreadCount - 1) })),

  resetUnread: () => set({ unreadCount: 0 }),

  setAlerts: (alerts: Alert[]) =>
    set({ alerts, unreadCount: alerts.filter((a) => !a.isRead).length }),

  markRead: (alertId: string) =>
    set((state) => {
      const alerts = state.alerts.map((a) => (a.id === alertId ? { ...a, isRead: true } : a));
      return { alerts, unreadCount: alerts.filter((a) => !a.isRead).length };
    }),

  markAllRead: () =>
    set((state) => ({
      alerts: state.alerts.map((a) => ({ ...a, isRead: true })),
      unreadCount: 0,
    })),
}));
