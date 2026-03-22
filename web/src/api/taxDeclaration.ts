import apiClient from "./client";

export interface TaxDeclaration {
  fiscalYear: number;
  regime: "MICRO_BNC" | "MICRO_BIC" | "REEL_SIMPLIFIE";
  businessType: string;

  // Revenue
  grossRevenue: number;
  vatCollected: number;
  netRevenue: number;

  // Micro abattement
  abattement: number;
  taxableIncome: number;

  // Charges
  chargesLoyer: number;
  chargesTelecom: number;
  chargesAssurance: number;
  chargesTransport: number;
  chargesAbonnements: number;
  chargesEnergie: number;
  chargesFournisseurs: number;
  chargesAutres: number;
  totalCharges: number;
  beneficeNet: number;

  // TVA
  tvaCollectee: number;
  tvaDeductible: number;
  tvaSolde: number;

  // Form boxes — keys are official French tax box codes
  formBoxes: Record<string, number>;

  // Warnings
  warnings: string[];

  // Counts
  totalInvoices: number;
  paidInvoices: number;
  uncategorizedTransactions: number;
}

export const taxDeclarationApi = {
  get: (year: number): Promise<TaxDeclaration> =>
    apiClient
      .get<TaxDeclaration>(`/api/tax-declaration?year=${year}`)
      .then((r) => r.data),
};
