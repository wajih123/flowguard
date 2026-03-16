import { create } from 'zustand';
import { MMKV } from 'react-native-mmkv';
import * as flowguardApi from '../api/flowguardApi';

const mmkv = new MMKV({ id: 'fg-auth' });
const FLAGS_KEY = 'fg-flags';
const FLAGS_TTL_MS = 5 * 60 * 1000; // 5 minutes

const DEFAULT_FLAGS: Record<string, boolean> = {
  RESERVE_ENABLED: false,
  ML_PREDICTIONS_ENABLED: true,
  B2B_SIGNUP_ENABLED: true,
  MAINTENANCE_MODE: false,
};

interface FeatureFlagState {
  flags: Record<string, boolean>
  lastFetched: number | null
  fetchFlags: () => Promise<void>
  isEnabled: (key: string) => boolean
}

export const useFeatureFlagStore = create<FeatureFlagState>((set, get) => ({
  flags: DEFAULT_FLAGS,
  lastFetched: null,

  fetchFlags: async () => {
    // Check MMKV cache
    const cached = mmkv.getString(FLAGS_KEY);
    if (cached) {
      try {
        const parsed = JSON.parse(cached) as { flags: Record<string, boolean>; ts: number };
        const age = Date.now() - parsed.ts;
        if (age < FLAGS_TTL_MS) {
          set({ flags: { ...DEFAULT_FLAGS, ...parsed.flags }, lastFetched: parsed.ts });
          return;
        }
      } catch {
        // Ignore parse errors, refetch
      }
    }

    try {
      const flags = await flowguardApi.getFeatureFlags();
      const merged = { ...DEFAULT_FLAGS, ...flags };
      mmkv.set(FLAGS_KEY, JSON.stringify({ flags: merged, ts: Date.now() }));
      set({ flags: merged, lastFetched: Date.now() });
    } catch {
      // Keep current flags on error
    }
  },

  isEnabled: (key: string) => get().flags[key] ?? false,
}));
