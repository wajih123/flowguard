import { create } from 'zustand'
import type { Account } from '../domain/Account'
import * as flowguardApi from '../api/flowguardApi'

interface AccountState {
  account: Account | null
  isLoading: boolean
  error: string | null
  fetchAccount: () => Promise<void>
  setAccount: (account: Account) => void
  clearAccount: () => void
}

export const useAccountStore = create<AccountState>((set) => ({
  account: null,
  isLoading: false,
  error: null,

  fetchAccount: async () => {
    set({ isLoading: true, error: null })
    try {
      const account = await flowguardApi.getCurrentAccount()
      set({ account, isLoading: false })
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Erreur de chargement du compte'
      set({ error: message, isLoading: false })
    }
  },

  setAccount: (account: Account) => set({ account }),

  clearAccount: () => set({ account: null, error: null }),
}))
