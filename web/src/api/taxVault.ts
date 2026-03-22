import apiClient from "./client";

export interface TaxVault {
  grossIncomeLast30Days: number;
  tvaReserve: number;
  urssafReserve: number;
  isReserve: number;
  totalReserved: number;
  currentBalance: number;
  spendableBalance: number;
  businessType: string;
}

export const taxVaultApi = {
  get: () => apiClient.get<TaxVault>("/api/tax-vault").then((r) => r.data),
};
