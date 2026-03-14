import axios from "axios";
import {
  useAuthStore,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from "@/store/authStore";

const BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  headers: { "Content-Type": "application/json" },
});

// ─── Request interceptor: inject Bearer token ───────────────────────────────
apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ─── Response interceptor: handle 401 / errors ──────────────────────────────
let isRefreshing = false;
let pendingQueue: Array<{
  resolve: (v: string) => void;
  reject: (e: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null) => {
  pendingQueue.forEach((p) => (error ? p.reject(error) : p.resolve(token!)));
  pendingQueue = [];
};

apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          pendingQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        });
      }
      originalRequest._retry = true;
      isRefreshing = true;
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        useAuthStore.getState().logout();
        throw error;
      }
      try {
        const { data } = await axios.post(`${BASE_URL}/api/auth/refresh`, {
          refreshToken,
        });
        setTokens(data.accessToken, data.refreshToken);
        apiClient.defaults.headers.common.Authorization = `Bearer ${data.accessToken}`;
        processQueue(null, data.accessToken);
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        throw refreshError;
      } finally {
        isRefreshing = false;
      }
    }
    throw error;
  },
);

export default apiClient;
