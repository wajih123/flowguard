import { create } from "zustand";

interface AlertState {
  unreadCount: number;
  setUnreadCount: (n: number) => void;
  incrementUnread: () => void;
  decrementUnread: () => void;
  resetUnread: () => void;
}

export const useAlertStore = create<AlertState>((set) => ({
  unreadCount: 0,
  setUnreadCount: (n) => set({ unreadCount: n }),
  incrementUnread: () => set((s) => ({ unreadCount: s.unreadCount + 1 })),
  decrementUnread: () =>
    set((s) => ({ unreadCount: Math.max(0, s.unreadCount - 1) })),
  resetUnread: () => set({ unreadCount: 0 }),
}));
