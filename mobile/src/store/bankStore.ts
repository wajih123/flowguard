import { create } from 'zustand';
import type { BankAccount } from '../domain/Account';

interface BankState {
  accounts: BankAccount[]
  isSyncing: boolean
  lastSyncAt: Date | null
  setAccounts: (accounts: BankAccount[]) => void
  setSyncing: (v: boolean) => void
  setLastSync: (date: Date) => void
}

export const useBankStore = create<BankState>((set) => ({
  accounts: [],
  isSyncing: false,
  lastSyncAt: null,

  setAccounts: (accounts: BankAccount[]) => set({ accounts }),
  setSyncing: (v: boolean) => set({ isSyncing: v }),
  setLastSync: (date: Date) => set({ lastSyncAt: date }),
}));
