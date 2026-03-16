import axios from 'axios';

const NORDIGEN_BASE_URL = 'https://bankaccountdata.gocardless.com/api/v2';

const nordigenClient = axios.create({
  baseURL: NORDIGEN_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

let nordigenAccessToken: string | null = null;

export const authenticate = async (): Promise<void> => {
  const secretId = process.env.EXPO_PUBLIC_NORDIGEN_SECRET_ID ?? 'demo_id';
  const secretKey = process.env.EXPO_PUBLIC_NORDIGEN_SECRET_KEY ?? 'demo_key';

  const { data } = await nordigenClient.post('/token/new/', {
    secret_id: secretId,
    secret_key: secretKey,
  });

  nordigenAccessToken = data.access;
  nordigenClient.defaults.headers.common.Authorization = `Bearer ${nordigenAccessToken}`;
};

const ensureAuthenticated = async (): Promise<void> => {
  if (!nordigenAccessToken) {
    await authenticate();
  }
};

export interface NordigenInstitution {
  id: string
  name: string
  bic: string
  logo: string
  countries: string[]
}

export const getInstitutions = async (): Promise<NordigenInstitution[]> => {
  await ensureAuthenticated();
  const { data } = await nordigenClient.get<NordigenInstitution[]>('/institutions/', {
    params: { country: 'fr' },
  });
  return data;
};

export interface NordigenRequisition {
  id: string
  link: string
  status: string
  accounts: string[]
}

export const createRequisition = async (
  institutionId: string,
  redirectUri: string,
  userId: string,
): Promise<NordigenRequisition> => {
  await ensureAuthenticated();
  const { data } = await nordigenClient.post<NordigenRequisition>('/requisitions/', {
    institution_id: institutionId,
    redirect: redirectUri,
    reference: userId,
    user_language: 'FR',
  });
  return data;
};

export const getRequisitionStatus = async (requisitionId: string): Promise<NordigenRequisition> => {
  await ensureAuthenticated();
  const { data } = await nordigenClient.get<NordigenRequisition>(
    `/requisitions/${encodeURIComponent(requisitionId)}/`,
  );
  return data;
};

export interface NordigenBalance {
  balanceAmount: { amount: string; currency: string }
  balanceType: string
  referenceDate: string
}

export const getAccountBalances = async (
  accountId: string,
): Promise<{ balances: NordigenBalance[] }> => {
  await ensureAuthenticated();
  const { data } = await nordigenClient.get<{ balances: NordigenBalance[] }>(
    `/accounts/${encodeURIComponent(accountId)}/balances/`,
  );
  return data;
};

export interface NordigenTransaction {
  transactionId: string
  bookingDate: string
  transactionAmount: { amount: string; currency: string }
  creditorName?: string
  debtorName?: string
  remittanceInformationUnstructured?: string
}

export const getAccountTransactions = async (
  accountId: string,
  dateFrom: string,
): Promise<{ transactions: { booked: NordigenTransaction[] } }> => {
  await ensureAuthenticated();
  const { data } = await nordigenClient.get(
    `/accounts/${encodeURIComponent(accountId)}/transactions/`,
    { params: { date_from: dateFrom } },
  );
  return data;
};

// ── Namespace object for screens that import nordigenApi as a single object ──
export const nordigenApi = {
  authenticate,
  getInstitutions,
  createRequisition: (redirectUri: string) =>
    createRequisition('demo_institution', redirectUri, 'demo_user'),
  getRequisitionStatus,
  getAccountBalances,
  getAccountTransactions,
};
