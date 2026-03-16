import type { AxiosInstance, InternalAxiosRequestConfig, AxiosError } from 'axios';
import Keychain from 'react-native-keychain';
import { FlowGuardError } from './errors';
import { useAuthStore } from '../store/authStore';

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: Error) => void
}> = [];

const processQueue = (error: Error | null, token: string | null) => {
  failedQueue.forEach((p) => {
    if (error) {
      p.reject(error);
    } else if (token) {
      p.resolve(token);
    }
  });
  failedQueue = [];
};

export const setupInterceptors = (api: AxiosInstance): void => {
  api.interceptors.request.use(
    async (config: InternalAxiosRequestConfig) => {
      try {
        const creds = await Keychain.getGenericPassword();
        if (creds && config.headers) {
          const { accessToken } = JSON.parse(creds.password) as {
            accessToken: string
            refreshToken: string
          };
          if (accessToken) {
            config.headers.Authorization = `Bearer ${accessToken}`;
          }
        }
      } catch {
        // no token — let it pass (public endpoints)
      }
      return config;
    },
    (error) => Promise.reject(error),
  );

  api.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const originalRequest = error.config;

      if (!error.response) {
        throw new FlowGuardError('NETWORK', 'Vérifiez votre connexion internet');
      }

      const status = error.response.status;

      if (status === 401 && originalRequest) {
        if (isRefreshing) {
          return new Promise<string>((resolve, reject) => {
            failedQueue.push({ resolve, reject });
          }).then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return api(originalRequest);
          });
        }

        isRefreshing = true;

        try {
          const creds = await Keychain.getGenericPassword();
          if (!creds) {
            throw new FlowGuardError('UNAUTHORIZED', 'Session expirée');
          }
          const { refreshToken } = JSON.parse(creds.password) as {
            accessToken: string
            refreshToken: string
          };
          if (!refreshToken) {
            throw new FlowGuardError('UNAUTHORIZED', 'Session expirée');
          }

          const { data } = await api.post('/api/auth/refresh', { refreshToken });
          const newToken: string = (data as { accessToken: string }).accessToken;
          const newRefreshToken: string = (data as { refreshToken: string }).refreshToken;

          await Keychain.setGenericPassword(
            'fg',
            JSON.stringify({ accessToken: newToken, refreshToken: newRefreshToken }),
          );

          processQueue(null, newToken);

          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
          }
          return api(originalRequest);
        } catch (refreshError) {
          processQueue(refreshError as Error, null);
          useAuthStore.getState().logout();
          throw new FlowGuardError('UNAUTHORIZED', 'Session expirée, veuillez vous reconnecter');
        } finally {
          isRefreshing = false;
        }
      }

      if (status >= 500) {
        throw new FlowGuardError('SERVER', 'Service temporairement indisponible', undefined, status);
      }

      if (status === 404) {
        throw new FlowGuardError('NOT_FOUND', 'Ressource introuvable', undefined, status);
      }

      if (status === 422) {
        const responseData = error.response.data as Record<string, unknown>;
        const fields = (responseData.fields as Record<string, string>) ?? undefined;
        throw new FlowGuardError(
          'VALIDATION',
          (responseData.message as string) ?? 'Données invalides',
          fields,
          status,
        );
      }

      throw new FlowGuardError(
        'UNKNOWN',
        (error.response.data as Record<string, string>)?.message ?? 'Erreur inconnue',
        undefined,
        status,
      );
    },
  );
};
