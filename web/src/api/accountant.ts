import apiClient from "./client";

export interface AccountantGrant {
  id: string;
  accountantEmail: string;
  accessToken: string;
  expiresAt: string;
  createdAt: string;
  expired: boolean;
}

export const accountantApi = {
  listGrants: () =>
    apiClient
      .get<AccountantGrant[]>("/api/accountant/grants")
      .then((r) => r.data),

  grantAccess: (email: string) =>
    apiClient
      .post<AccountantGrant>(
        `/api/accountant/grants?email=${encodeURIComponent(email)}`,
      )
      .then((r) => r.data),

  revokeAccess: (grantId: string) =>
    apiClient.delete(`/api/accountant/grants/${grantId}`),

  downloadFec: (token: string, year: number) =>
    apiClient
      .get<string>(`/api/accountant/portal/fec?year=${year}`, {
        headers: { "X-Accountant-Token": token },
        responseType: "text",
      })
      .then((r) => r.data),

  /** Owner downloads their own FEC (JWT-authenticated). */
  ownerDownloadFec: (year: number) =>
    apiClient
      .get<string>(`/api/export/fec?from=${year}-01-01&to=${year}-12-31`, {
        responseType: "text",
      })
      .then((r) => r.data),
};
