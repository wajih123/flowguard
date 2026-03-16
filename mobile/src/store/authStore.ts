import { create } from 'zustand';
import type { User, RegisterDto, RegisterBusinessDto } from '../domain/User';
import { isMfaChallenge } from '../domain/User';
import * as flowguardApi from '../api/flowguardApi';
import Keychain from 'react-native-keychain';
import ReactNativeBiometrics from 'react-native-biometrics';
import { MMKV } from 'react-native-mmkv';

const mmkv = new MMKV({ id: 'fg-auth' });
const rnBiometrics = new ReactNativeBiometrics();

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
  // 2FA state
  mfaPending: boolean
  sessionToken: string | null
  maskedEmail: string | null
  login: (email: string, password: string) => Promise<void>
  verifyOtp: (code: string) => Promise<void>
  cancelMfa: () => void
  loginWithBiometric: () => Promise<void>
  register: (data: RegisterDto) => Promise<void>
  registerBusiness: (data: RegisterBusinessDto) => Promise<void>
  logout: () => Promise<void>
  hydrate: () => Promise<void>
  refreshTokens: () => Promise<string>
  clearError: () => void
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  mfaPending: false,
  sessionToken: null,
  maskedEmail: null,

  login: async (email: string, password: string) => {
    set({ isLoading: true, error: null });
    try {
      const response = await flowguardApi.login(email, password);
      if (isMfaChallenge(response)) {
        set({
          mfaPending: true,
          sessionToken: response.sessionToken,
          maskedEmail: response.maskedEmail,
          isLoading: false,
        });
      } else {
        await Keychain.setGenericPassword(
          'fg',
          JSON.stringify({
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
          }),
        );
        mmkv.set('fg-user', JSON.stringify(response.user));
        set({
          user: response.user,
          isAuthenticated: true,
          mfaPending: false,
          sessionToken: null,
          maskedEmail: null,
          isLoading: false,
        });
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Erreur de connexion';
      set({ error: message, isLoading: false });
    }
  },

  verifyOtp: async (code: string) => {
    const { sessionToken } = get();
    if (!sessionToken) {
      set({ error: 'Session expirée. Veuillez vous reconnecter.', mfaPending: false });
      return;
    }
    set({ isLoading: true, error: null });
    try {
      const response = await flowguardApi.verifyOtp(sessionToken, code);
      await Keychain.setGenericPassword(
        'fg',
        JSON.stringify({ accessToken: response.accessToken, refreshToken: response.refreshToken }),
      );
      mmkv.set('fg-user', JSON.stringify(response.user));
      set({
        user: response.user,
        isAuthenticated: true,
        mfaPending: false,
        sessionToken: null,
        maskedEmail: null,
        isLoading: false,
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Code incorrect ou expiré';
      set({ error: message, isLoading: false });
    }
  },

  cancelMfa: () => set({ mfaPending: false, sessionToken: null, maskedEmail: null, error: null }),

  loginWithBiometric: async () => {
    set({ isLoading: true, error: null });
    try {
      const { available } = await rnBiometrics.isSensorAvailable();
      if (!available) {
        set({ error: 'Biométrie non disponible', isLoading: false });
        return;
      }
      const { success } = await rnBiometrics.simplePrompt({
        promptMessage: 'Connexion à FlowGuard',
      });
      if (!success) {
        set({ error: 'Authentification biométrique échouée', isLoading: false });
        return;
      }
      const creds = await Keychain.getGenericPassword();
      if (!creds) {
        set({ error: 'Aucune session sauvegardée', isLoading: false });
        return;
      }
      const { refreshToken } = JSON.parse(creds.password) as {
        accessToken: string
        refreshToken: string
      };
      // Validate accessToken by calling /api/users/me
      try {
        const user = await flowguardApi.getCurrentUser();
        mmkv.set('fg-user', JSON.stringify(user));
        set({ user, isAuthenticated: true, isLoading: false });
      } catch {
        // Try refresh
        try {
          const refreshed = await flowguardApi.refreshToken(refreshToken);
          await Keychain.setGenericPassword('fg', JSON.stringify(refreshed));
          const user = await flowguardApi.getCurrentUser();
          mmkv.set('fg-user', JSON.stringify(user));
          set({ user, isAuthenticated: true, isLoading: false });
        } catch {
          await Keychain.resetGenericPassword();
          mmkv.clearAll();
          set({ user: null, isAuthenticated: false, isLoading: false });
        }
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Erreur de connexion biométrique';
      set({ error: message, isLoading: false });
    }
  },

  register: async (data: RegisterDto) => {
    set({ isLoading: true, error: null });
    try {
      const response = await flowguardApi.register(data);
      await Keychain.setGenericPassword(
        'fg',
        JSON.stringify({ accessToken: response.accessToken, refreshToken: response.refreshToken }),
      );
      mmkv.set('fg-user', JSON.stringify(response.user));
      set({ user: response.user, isAuthenticated: true, isLoading: false });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Erreur d'inscription";
      set({ error: message, isLoading: false });
    }
  },

  registerBusiness: async (data: RegisterBusinessDto) => {
    set({ isLoading: true, error: null });
    try {
      const response = await flowguardApi.registerBusiness(data);
      await Keychain.setGenericPassword(
        'fg',
        JSON.stringify({ accessToken: response.accessToken, refreshToken: response.refreshToken }),
      );
      mmkv.set('fg-user', JSON.stringify(response.user));
      set({ user: response.user, isAuthenticated: true, isLoading: false });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Erreur d'inscription entreprise";
      set({ error: message, isLoading: false });
    }
  },

  logout: async () => {
    try {
      const creds = await Keychain.getGenericPassword();
      if (creds) {
        const { refreshToken } = JSON.parse(creds.password) as {
          refreshToken: string
          accessToken: string
        };
        await flowguardApi.logoutApi(refreshToken);
      }
    } catch {
      // Ignore logout API errors
    } finally {
      await Keychain.resetGenericPassword();
      mmkv.clearAll();
      set({ user: null, isAuthenticated: false, error: null });
    }
  },

  hydrate: async () => {
    set({ isLoading: true });
    try {
      const creds = await Keychain.getGenericPassword();
      if (!creds) {
        set({ isLoading: false });
        return;
      }
      const { refreshToken } = JSON.parse(creds.password) as {
        accessToken: string
        refreshToken: string
      };
      try {
        const user = await flowguardApi.getCurrentUser();
        mmkv.set('fg-user', JSON.stringify(user));
        set({ user, isAuthenticated: true, isLoading: false });
      } catch {
        // Try refresh
        try {
          const refreshed = await flowguardApi.refreshToken(refreshToken);
          await Keychain.setGenericPassword('fg', JSON.stringify(refreshed));
          const user = await flowguardApi.getCurrentUser();
          mmkv.set('fg-user', JSON.stringify(user));
          set({ user, isAuthenticated: true, isLoading: false });
        } catch {
          await Keychain.resetGenericPassword();
          mmkv.clearAll();
          set({ user: null, isAuthenticated: false, isLoading: false });
        }
      }
    } catch {
      await Keychain.resetGenericPassword();
      mmkv.clearAll();
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },

  refreshTokens: async () => {
    const creds = await Keychain.getGenericPassword();
    if (!creds) {throw new Error('No credentials stored');}
    const { refreshToken } = JSON.parse(creds.password) as {
      accessToken: string
      refreshToken: string
    };
    const refreshed = await flowguardApi.refreshToken(refreshToken);
    await Keychain.setGenericPassword('fg', JSON.stringify(refreshed));
    return refreshed.accessToken;
  },

  clearError: () => set({ error: null }),
}));
