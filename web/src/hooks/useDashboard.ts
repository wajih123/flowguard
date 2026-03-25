import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { addDays, format } from "date-fns";
import { dashboardApi } from "@/api/dashboard";
import type { DashboardSummary, SpendingCategory } from "@/api/dashboard";
import { sweepApi } from "@/api/financial";
import type { SweepSuggestion } from "@/api/financial";
import type { DashboardData, Transaction } from "@/types";

const _today = new Date();
_today.setHours(0, 0, 0, 0);

const mockDashboard: DashboardData = {
  account: {
    id: "acc-001",
    bankName: "BNP Paribas",
    ibanMasked: "FR76****4521",
    currentBalance: 3240.0,
    currency: "EUR",
    lastSyncAt: new Date().toISOString(),
    syncStatus: "OK",
  },
  currentBalance: 3240.0,
  predictedBalance30d: 1890.0,
  balanceTrend: -1350.0,
  healthScore: 78,
  healthLabel: "Bonne santé",
  reserveAvailable: false,
  reserveMaxAmount: 2000,
  hasHighAlert: true,
  highAlertMessage:
    "Votre solde prévu sera de -230 € le 18 mars. URSSAF de 850 € attendue.",
  highAlertAmount: -230,
  highAlertDate: format(addDays(_today, 5), "yyyy-MM-dd"),
  accountCount: 1,
};

const mockTransactions: Transaction[] = [
  {
    id: "tx-001",
    label: "Loyer appartement",
    amount: -1200.0,
    currency: "EUR",
    transactionDate: format(addDays(_today, -2), "yyyy-MM-dd"),
    category: "LOGEMENT",
    isRecurring: true,
  },
  {
    id: "tx-002",
    label: "Client SARL Dupont",
    amount: 2400.0,
    currency: "EUR",
    transactionDate: format(addDays(_today, -3), "yyyy-MM-dd"),
    category: "REVENU",
    isRecurring: false,
  },
  {
    id: "tx-003",
    label: "SFR Mobile Pro",
    amount: -35.99,
    currency: "EUR",
    transactionDate: format(addDays(_today, -4), "yyyy-MM-dd"),
    category: "TELECOM",
    isRecurring: true,
  },
  {
    id: "tx-004",
    label: "Carrefour Market",
    amount: -124.5,
    currency: "EUR",
    transactionDate: format(addDays(_today, -5), "yyyy-MM-dd"),
    category: "ALIMENTATION",
    isRecurring: false,
  },
  {
    id: "tx-005",
    label: "Amazon AWS",
    amount: -48.0,
    currency: "EUR",
    transactionDate: format(addDays(_today, -6), "yyyy-MM-dd"),
    category: "TECH",
    isRecurring: true,
  },
];

const isMock = import.meta.env.VITE_MOCK_DATA === "true";

export const useDashboard = () =>
  useQuery<DashboardData>({
    queryKey: ["dashboard"],
    queryFn: isMock
      ? () =>
          new Promise<DashboardData>((resolve) =>
            setTimeout(() => resolve(mockDashboard), 600),
          )
      : dashboardApi.getSummary,
    staleTime: 5 * 60 * 1000,
  });

export const useDashboardTransactions = () =>
  useQuery<Transaction[]>({
    queryKey: ["dashboard-transactions"],
    queryFn: isMock
      ? () =>
          new Promise<Transaction[]>((resolve) =>
            setTimeout(() => resolve(mockTransactions), 400),
          )
      : () => dashboardApi.getTransactions(5),
    staleTime: 5 * 60 * 1000,
  });

export const useEnrichedDashboard = () =>
  useQuery<DashboardSummary>({
    queryKey: ["dashboard-enriched"],
    queryFn: dashboardApi.getEnrichedSummary,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

export const useSpendingByCategory = (months = 1) =>
  useQuery<SpendingCategory[]>({
    queryKey: ["spending-by-category", months],
    queryFn: () => dashboardApi.getSpendingByCategory(months),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

export const useSweepSuggestions = () => {
  const qc = useQueryClient();
  const query = useQuery<SweepSuggestion[]>({
    queryKey: ["sweep-suggestions"],
    queryFn: sweepApi.list,
    staleTime: 2 * 60 * 1000,
    retry: false,
  });
  const accept = useMutation({
    mutationFn: sweepApi.accept,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["sweep-suggestions"] }),
  });
  const dismiss = useMutation({
    mutationFn: sweepApi.dismiss,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["sweep-suggestions"] }),
  });
  return { ...query, accept, dismiss };
};
