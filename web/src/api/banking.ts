import apiClient from "./client";

export interface BankAccount {
  id: string;
  bankName: string;
  accountName: string;
  ibanMasked: string | null;
  balance: number;
  currency: string;
  accountType: string;
  syncStatus: "PENDING" | "SYNCING" | "OK" | "ERROR";
  lastSyncAt: string | null;
}

export interface ConnectStartResponse {
  connect_url: string;
  state: string;
}

export interface SyncResponse {
  status?: string; // "syncing" when async (202)
  accounts_synced?: number;
  synced_at?: string;
}

export const bankingApi = {
  /** Démarre le flow de connexion Bridge → retourne l'URL de redirect */
  startConnect: async (): Promise<ConnectStartResponse> => {
    const { data } = await apiClient.post<ConnectStartResponse>(
      "/api/banking/connect/start",
    );
    return data;
  },

  /** Finalise la connexion après le callback Bridge (step 2) */
  handleCallback: async (state: string): Promise<SyncResponse> => {
    const { data } = await apiClient.post<SyncResponse>(
      "/api/banking/connect/callback",
      { state },
      { timeout: 15_000 },
    );
    return data;
  },

  /** Synchronisation manuelle des comptes */
  sync: async (): Promise<SyncResponse> => {
    const { data } = await apiClient.post<SyncResponse>(
      "/api/banking/sync",
      undefined,
      { timeout: 15_000 },
    );
    return data;
  },

  /** Liste les comptes connectés */
  getAccounts: async (): Promise<BankAccount[]> => {
    const { data } = await apiClient.get<BankAccount[]>(
      "/api/banking/accounts",
    );
    return data;
  },
};
