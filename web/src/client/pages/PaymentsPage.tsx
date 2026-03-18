import React, { useState } from "react";
import { Send, XCircle, Clock, CheckCircle, AlertTriangle } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Loader } from "@/components/ui/Loader";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { paymentsApi } from "@/api/payments";
import type { InitiatePaymentRequest, PaymentInitiation } from "@/api/payments";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const STATUS_CONFIG = {
  PENDING: {
    label: "En attente",
    color: "text-text-muted",
    bg: "bg-white/[0.05]",
    icon: Clock,
  },
  SUBMITTED: {
    label: "Soumis",
    color: "text-primary",
    bg: "bg-primary/10",
    icon: Send,
  },
  EXECUTED: {
    label: "Exécuté",
    color: "text-success",
    bg: "bg-success/10",
    icon: CheckCircle,
  },
  REJECTED: {
    label: "Rejeté",
    color: "text-danger",
    bg: "bg-danger/10",
    icon: AlertTriangle,
  },
  CANCELLED: {
    label: "Annulé",
    color: "text-text-muted",
    bg: "bg-white/[0.03]",
    icon: XCircle,
  },
} as const;

// ── IBAN mask display ─────────────────────────────────────────────────────────
const maskIban = (iban: string) => {
  const clean = iban.replace(/\s/g, "");
  return clean.length > 8
    ? `${clean.slice(0, 4)} **** **** ${clean.slice(-4)}`
    : iban;
};

// ── Payment Row ───────────────────────────────────────────────────────────────
const PaymentRow: React.FC<{ p: PaymentInitiation; onCancel: () => void }> = ({
  p,
  onCancel,
}) => {
  const cfg = STATUS_CONFIG[p.status];
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-3.5 bg-white/[0.02] hover:bg-white/[0.04] border border-white/[0.06] rounded-xl transition">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <p className="font-medium text-white truncate">{p.creditorName}</p>
          <span
            className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full ${cfg.bg} ${cfg.color}`}
          >
            <cfg.icon size={11} />
            {cfg.label}
          </span>
        </div>
        <p className="text-text-muted text-xs">
          {maskIban(p.creditorIban)} · {p.reference}
          {" · "}
          {format(parseISO(p.initiatedAt), "d MMM yyyy", { locale: fr })}
        </p>
      </div>
      <div className="text-right shrink-0">
        <p className="font-numeric text-white font-semibold">{fmt(p.amount)}</p>
        <p className="text-text-muted text-xs">{p.currency}</p>
      </div>
      {(p.status === "PENDING" || p.status === "SUBMITTED") && (
        <Button
          variant="ghost"
          size="sm"
          className="text-danger shrink-0"
          onClick={onCancel}
        >
          Annuler
        </Button>
      )}
    </div>
  );
};

// ── SEPA Initiation Form ──────────────────────────────────────────────────────
const PaymentForm: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const qc = useQueryClient();
  const [form, setForm] = useState<InitiatePaymentRequest>({
    creditorName: "",
    creditorIban: "",
    amount: 0,
    currency: "EUR",
    reference: "",
  });
  const [ibanError, setIbanError] = useState("");

  const IBAN_RE = /^[A-Z]{2}\d{2}[A-Z0-9]{4}\d{7}([A-Z0-9]?){0,16}$/;

  const mutation = useMutation({
    mutationFn: () =>
      paymentsApi.initiate(
        form,
        `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["payments"] });
      onClose();
    },
  });

  const validateIban = (iban: string) => {
    const clean = iban.replace(/\s/g, "").toUpperCase();
    if (clean && !IBAN_RE.test(clean)) {
      setIbanError("IBAN invalide. Format attendu : FR76 xxxx xxxx xxxx");
    } else {
      setIbanError("");
    }
  };

  const canSubmit =
    form.creditorName &&
    form.creditorIban &&
    !ibanError &&
    form.amount > 0 &&
    form.reference;

  return (
    <Card>
      <CardHeader title="Initier un virement SEPA" />
      <div className="mt-5 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Bénéficiaire
            </label>
            <input
              className="fg-input"
              value={form.creditorName}
              onChange={(e) =>
                setForm({ ...form, creditorName: e.target.value })
              }
              placeholder="Nom du bénéficiaire"
            />
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              IBAN bénéficiaire
            </label>
            <input
              className={`fg-input ${ibanError ? "border-danger/50" : ""}`}
              value={form.creditorIban}
              onChange={(e) => {
                setForm({ ...form, creditorIban: e.target.value });
                validateIban(e.target.value);
              }}
              placeholder="FR76 3000 4028 3798 7654 3210 943"
            />
            {ibanError && (
              <p className="text-danger text-xs mt-1">{ibanError}</p>
            )}
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Montant (€)
            </label>
            <input
              className="fg-input"
              type="number"
              min="0.01"
              step="0.01"
              value={form.amount}
              onChange={(e) =>
                setForm({ ...form, amount: parseFloat(e.target.value) || 0 })
              }
            />
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Référence
            </label>
            <input
              className="fg-input"
              value={form.reference}
              onChange={(e) => setForm({ ...form, reference: e.target.value })}
              placeholder="REF-2025-001"
              maxLength={140}
            />
          </div>
        </div>
        <p className="text-text-muted text-xs bg-white/[0.03] rounded-lg px-3 py-2 border border-white/[0.06]">
          ⚡ Les virements sont exécutés via Swan (Open Banking) sous 1 jour
          ouvré dans la zone SEPA.
        </p>
        <div className="flex gap-3 justify-end">
          <Button variant="ghost" onClick={onClose}>
            Annuler
          </Button>
          <Button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !canSubmit}
          >
            {mutation.isPending ? "Envoi…" : "Envoyer le virement"}
          </Button>
        </div>
      </div>
    </Card>
  );
};

// ── Main Page ─────────────────────────────────────────────────────────────────
const PaymentsPage: React.FC = () => {
  const [showForm, setShowForm] = useState(false);
  const qc = useQueryClient();

  const { data: payments, isLoading } = useQuery({
    queryKey: ["payments"],
    queryFn: paymentsApi.list,
  });

  const cancelMut = useMutation({
    mutationFn: paymentsApi.cancel,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["payments"] }),
  });

  const totalExecuted =
    payments
      ?.filter((p) => p.status === "EXECUTED")
      .reduce((s, p) => s + p.amount, 0) ?? 0;
  const pending =
    payments?.filter((p) => p.status === "SUBMITTED" || p.status === "PENDING")
      .length ?? 0;

  return (
    <Layout title="Virements">
      <div className="max-w-4xl mx-auto space-y-6 animate-slide-up">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="page-title">Virements SEPA</h1>
            <p className="page-subtitle">
              Initiez et suivez vos paiements via Open Banking
            </p>
          </div>
          <Button onClick={() => setShowForm(true)} className="gap-2">
            <Send size={15} /> Nouveau virement
          </Button>
        </div>

        {showForm && <PaymentForm onClose={() => setShowForm(false)} />}

        {/* KPIs */}
        <div className="grid grid-cols-3 gap-4">
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
              Total exécuté
            </p>
            <p className="text-2xl font-bold font-numeric text-white">
              {fmt(totalExecuted)}
            </p>
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
              En attente
            </p>
            <p
              className={`text-2xl font-bold font-numeric ${pending > 0 ? "text-warning" : "text-success"}`}
            >
              {pending}
            </p>
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
              Total virements
            </p>
            <p className="text-2xl font-bold font-numeric text-white">
              {payments?.length ?? 0}
            </p>
          </Card>
        </div>

        {/* History */}
        <Card>
          <CardHeader title="Historique des virements" />
          {isLoading ? (
            <Loader text="Chargement…" />
          ) : !payments?.length ? (
            <div className="py-12 text-center text-text-muted">
              <Send className="mx-auto mb-3 opacity-30" size={36} />
              <p>Aucun virement. Initiez votre premier paiement SEPA.</p>
            </div>
          ) : (
            <div className="mt-4 space-y-2">
              {payments.map((p) => (
                <PaymentRow
                  key={p.id}
                  p={p}
                  onCancel={() => cancelMut.mutate(p.id)}
                />
              ))}
            </div>
          )}
        </Card>
      </div>
    </Layout>
  );
};

export default PaymentsPage;
