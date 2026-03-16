import apiClient from "./client";
import type {
  AuthResponse,
  LoginRequest,
  LoginResult,
  RegisterRequest,
  User,
} from "@/domain/User";

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<LoginResult>("/api/auth/login", data).then((r) => r.data),

  verifyOtp: (sessionToken: string, code: string) =>
    apiClient
      .post<AuthResponse>("/api/auth/verify-otp", { sessionToken, code })
      .then((r) => r.data),

  register: (data: RegisterRequest) =>
    apiClient
      .post<AuthResponse>("/api/auth/register", data)
      .then((r) => r.data),

  refresh: (refreshToken: string) =>
    apiClient
      .post<AuthResponse>("/api/auth/refresh", { refreshToken })
      .then((r) => r.data),

  logout: (refreshToken: string) =>
    apiClient.post("/api/auth/logout", { refreshToken }),

  me: () => apiClient.get<User>("/api/auth/me").then((r) => r.data),
};
