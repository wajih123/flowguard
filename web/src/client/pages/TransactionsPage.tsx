import React, { useState } from "react";
import {
  ArrowUpCircle,
  ArrowDownCircle,
  RefreshCw,
  Search,
  Filter,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Input, Select } from "@/components/ui/Input";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loader } from "@/components/ui/Loader";
import { useTransactions } from "@/hooks/useTransactions";
import { useAccounts } from "@/hooks/useAccounts";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";
import type { TransactionCategory } from "@/domain/Transaction";

const CATEGORY_LABELS: Record<TransactionCategory, string> = {
  LOYER: "Loyer",
  SALAIRE: "Salaires",
  ALIMENTATION: "Alimentation",
  TRANSPORT: "Transport",
  ABONNEMENT: "Abonnements",
  ENERGIE: "Énergie",
  TELECOM: "Télécom",
  ASSURANCE: "Assurance",
  CHARGES_FISCALES: "Charges fiscales",
  FOURNISSEUR: "Fournisseurs",
  CLIENT_PAYMENT: "Paiements clients",
  VIREMENT: "Virements",
  AUTRE: "Autre",
};

const fmt = (n: number, type: string) =>
  `${type === "CREDIT" ? "+" : "-"}${new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
  }).format(Math.abs(n))}`;

const TransactionsPage: React.FC = () => {
  const { data: accounts } = useAccounts();
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");

  const accountId = accounts?.[0]?.id ?? "";
  const { data: transactions, isLoading } = useTransactions(accountId, {
    category: categoryFilter || undefined,
  });

  const filtered = (transactions ?? []).filter(
    (t) => !search || t.label.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <Layout title="Transactions">
      <div className="max-w-5xl mx-auto space-y-6 animate-fade-in">
        <div className="page-header">
          <h1 className="page-title">Transactions</h1>
          <p className="page-subtitle">
            Historique de vos opérations bancaires
          </p>
        </div>

        {/* Filters */}
        <div className="flex gap-3">
          <div className="flex-1">
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Rechercher une transaction…"
              leftIcon={<Search size={16} />}
            />
          </div>
          <div className="w-56">
            <Select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              options={[
                { value: "", label: "Toutes catégories" },
                ...Object.entries(CATEGORY_LABELS).map(([v, l]) => ({
                  value: v,
                  label: l,
                })),
              ]}
            />
          </div>
        </div>

        {/* Stats */}
        {transactions && (
          <div className="grid grid-cols-3 gap-4">
            <Card className="stat-card">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
                Total opérations
              </p>
              <p className="text-2xl font-bold text-white">
                {transactions.length}
              </p>
            </Card>
            <Card className="stat-card">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
                Entrées
              </p>
              <p className="text-2xl font-bold text-success">
                +
                {new Intl.NumberFormat("fr-FR", {
                  style: "currency",
                  currency: "EUR",
                  maximumFractionDigits: 0,
                }).format(
                  transactions
                    .filter((t) => t.type === "CREDIT")
                    .reduce((s, t) => s + t.amount, 0),
                )}
              </p>
            </Card>
            <Card className="stat-card">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
                Sorties
              </p>
              <p className="text-2xl font-bold text-danger">
                -
                {new Intl.NumberFormat("fr-FR", {
                  style: "currency",
                  currency: "EUR",
                  maximumFractionDigits: 0,
                }).format(
                  transactions
                    .filter((t) => t.type === "DEBIT")
                    .reduce((s, t) => s + t.amount, 0),
                )}
              </p>
            </Card>
          </div>
        )}

        {/* Table */}
        <Card padding="none">
          <div className="p-6 border-b border-white/[0.06]">
            <CardHeader title="Opérations" icon={<RefreshCw size={18} />} />
          </div>
          {isLoading ? (
            <Loader />
          ) : filtered.length === 0 ? (
            <EmptyState
              title="Aucune transaction"
              description="Aucune opération ne correspond à vos filtres"
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Libellé</th>
                    <th>Catégorie</th>
                    <th>Récurrent</th>
                    <th className="text-right">Montant</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((t) => (
                    <tr key={t.id}>
                      <td className="text-text-secondary text-xs whitespace-nowrap">
                        {format(parseISO(t.date), "d MMM yyyy", { locale: fr })}
                      </td>
                      <td className="font-medium max-w-xs truncate">
                        {t.label}
                      </td>
                      <td>
                        <Badge variant="muted" className="text-xs">
                          {CATEGORY_LABELS[t.category] ?? t.category}
                        </Badge>
                      </td>
                      <td>
                        {t.isRecurring && (
                          <Badge variant="primary">Récurrent</Badge>
                        )}
                      </td>
                      <td
                        className={`text-right font-semibold tabular-nums ${
                          t.type === "CREDIT" ? "text-success" : "text-danger"
                        }`}
                      >
                        {fmt(t.amount, t.type)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </Layout>
  );
};

export default TransactionsPage;
