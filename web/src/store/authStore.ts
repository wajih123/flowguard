import { create } from "zustand";
import { authApi } from "@/api/auth";
import { isMfaChallenge } from "@/domain/User";
import type { User, LoginRequest, RegisterRequest, Role } from "@/domain/User";

// ─── Token storage — persisted in localStorage ───────────────────────────────
const TOKEN_KEY = "fg_access";
const REFRESH_KEY = "fg_refresh";

export const getAccessToken = () => localStorage.getItem(TOKEN_KEY);
export const getRefreshToken = () => localStorage.getItem(REFRESH_KEY);
export const setTokens = (access: string, refresh: string) => {
  localStorage.setItem(TOKEN_KEY, access);
  localStorage.setItem(REFRESH_KEY, refresh);
};
export const clearTokens = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
};

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  isAdmin: boolean;
  // 2FA state
  mfaPending: boolean;
  sessionToken: string | null;
  maskedEmail: string | null;
}

interface AuthActions {
  login: (data: LoginRequest) => Promise<void>;
  verifyOtp: (code: string) => Promise<void>;
  cancelMfa: () => void;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  hydrate: () => Promise<void>;
  refreshTokens: () => Promise<void>;
  clearError: () => void;
}

const ADMIN_ROLES = new Set<Role>(["ROLE_ADMIN", "ROLE_SUPER_ADMIN"]);
const checkAdmin = (user: User) => !!user.role && ADMIN_ROLES.has(user.role);

export const useAuthStore = create<AuthState & AuthActions>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true, // true until hydrate() resolves — prevents redirect before token check
  error: null,
  isAdmin: false,
  mfaPending: false,
  sessionToken: null,
  maskedEmail: null,

  login: async (data) => {
    set({ isLoading: true, error: null });
    try {
      const res = await authApi.login(data);
      if (isMfaChallenge(res)) {
        set({
          mfaPending: true,
          sessionToken: res.sessionToken,
          maskedEmail: res.maskedEmail,
          isLoading: false,
        });
      } else {
        setTokens(res.accessToken, res.refreshToken);
        set({
          user: res.user,
          isAuthenticated: true,
          isAdmin: checkAdmin(res.user),
          mfaPending: false,
          sessionToken: null,
          maskedEmail: null,
          isLoading: false,
        });
      }
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : "Email ou mot de passe incorrect.";
      set({ error: msg, isLoading: false });
      throw err;
    }
  },

  verifyOtp: async (code) => {
    const { sessionToken } = get();
    if (!sessionToken) {
      set({
        error: "Session expirée. Veuillez vous reconnecter.",
        mfaPending: false,
      });
      return;
    }
    set({ isLoading: true, error: null });
    try {
      const res = await authApi.verifyOtp(sessionToken, code);
      setTokens(res.accessToken, res.refreshToken);
      set({
        user: res.user,
        isAuthenticated: true,
        isAdmin: checkAdmin(res.user),
        mfaPending: false,
        sessionToken: null,
        maskedEmail: null,
        isLoading: false,
      });
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : "Code incorrect ou expiré.";
      set({ error: msg, isLoading: false });
      throw err;
    }
  },

  cancelMfa: () =>
    set({
      mfaPending: false,
      sessionToken: null,
      maskedEmail: null,
      error: null,
    }),

  register: async (data) => {
    set({ isLoading: true, error: null });
    try {
      const res = await authApi.register(data);
      setTokens(res.accessToken, res.refreshToken);
      set({
        user: res.user,
        isAuthenticated: true,
        isAdmin: checkAdmin(res.user),
        isLoading: false,
      });
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : "Erreur lors de l'inscription.";
      set({ error: msg, isLoading: false });
      throw err;
    }
  },

  logout: () => {
    const rt = getRefreshToken() ?? "";
    authApi.logout(rt).catch(() => {});
    clearTokens();
    set({ user: null, isAuthenticated: false, isAdmin: false, error: null });
  },

  hydrate: async () => {
    const access = getAccessToken();
    const refresh = getRefreshToken();
    if (!access || !refresh) {
      set({ isLoading: false });
      return;
    }
    // Already authenticated in this session (e.g., just completed MFA/login)
    // Skipping refresh here avoids consuming a single-use refresh token unnecessarily.
    if (get().isAuthenticated && get().user) {
      set({ isLoading: false });
      return;
    }
    set({ isLoading: true });
    try {
      // Try to refresh to get a fresh token + user info
      const res = await authApi.refresh(refresh);
      setTokens(res.accessToken, res.refreshToken);
      set({
        user: res.user,
        isAuthenticated: true,
        isAdmin: checkAdmin(res.user),
        isLoading: false,
      });
    } catch {
      // Refresh token expired — clear and force re-login
      clearTokens();
      set({
        user: null,
        isAuthenticated: false,
        isAdmin: false,
        isLoading: false,
      });
    }
  },

  refreshTokens: async () => {
    const rt = getRefreshToken();
    if (!rt) throw new Error("No refresh token");
    const res = await authApi.refresh(rt);
    setTokens(res.accessToken, res.refreshToken);
  },

  clearError: () => set({ error: null }),
}));
