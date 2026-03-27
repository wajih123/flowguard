import React, { useState } from "react";
import {
  FileText,
  Plus,
  Send,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Bell,
  BellOff,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { invoicesApi } from "@/api/invoices";
import type { Invoice, CreateInvoiceRequest } from "@/api/invoices";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const STATUS_CONFIG = {
  DRAFT: {
    label: "Brouillon",
    color: "text-text-muted",
    bg: "bg-white/[0.05]",
    icon: FileText,
  },
  SENT: {
    label: "Envoyée",
    color: "text-primary",
    bg: "bg-primary/10",
    icon: Send,
  },
  OVERDUE: {
    label: "En retard",
    color: "text-danger",
    bg: "bg-danger/10",
    icon: AlertTriangle,
  },
  PAID: {
    label: "Payée",
    color: "text-success",
    bg: "bg-success/10",
    icon: CheckCircle,
  },
  CANCELLED: {
    label: "Annulée",
    color: "text-text-muted",
    bg: "bg-white/[0.03]",
    icon: XCircle,
  },
} as const;

// ── Invoice Row ───────────────────────────────────────────────────────────────
const InvoiceRow: React.FC<{
  inv: Invoice;
  onSend: () => void;
  onMarkPaid: () => void;
  onCancel: () => void;
  onToggleReminder: () => void;
}> = ({ inv, onSend, onMarkPaid, onCancel, onToggleReminder }) => {
  const cfg = STATUS_CONFIG[inv.status];
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-3.5 bg-white/[0.02] hover:bg-white/[0.04] border border-white/[0.06] rounded-xl transition">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <p className="font-medium text-white truncate">{inv.clientName}</p>
          <span
            className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full ${cfg.bg} ${cfg.color}`}
          >
            <cfg.icon size={11} />
            {cfg.label}
          </span>
        </div>
        <p className="text-text-muted text-xs">
          N° {inv.number} · Émise le{" "}
          {format(parseISO(inv.issueDate), "d MMM yyyy", { locale: fr })} ·
          Échéance {format(parseISO(inv.dueDate), "d MMM yyyy", { locale: fr })}
          {inv.daysOverdue !== null && inv.daysOverdue > 0 && (
            <span className="text-danger ml-1">
              ({inv.daysOverdue}j de retard)
            </span>
          )}
        </p>
      </div>
      <div className="text-right shrink-0">
        <p className="font-numeric text-white font-semibold">
          {fmt(inv.totalTtc)}
        </p>
        <p className="text-text-muted text-xs">
          TTC · TVA {inv.vatRate == null ? "incluse" : `${inv.vatRate}%`}
        </p>
      </div>
      <div className="flex gap-2 shrink-0">
        {(inv.status === "SENT" || inv.status === "OVERDUE") && (
          <button
            onClick={onToggleReminder}
            title={
              inv.reminderEnabled
                ? "Désactiver les rappels automatiques"
                : "Activer les rappels automatiques"
            }
            className={`p-1.5 rounded-lg transition ${
              inv.reminderEnabled
                ? "text-primary bg-primary/10 hover:bg-primary/20"
                : "text-text-muted hover:text-white hover:bg-white/[0.05]"
            }`}
          >
            {inv.reminderEnabled ? <Bell size={14} /> : <BellOff size={14} />}
          </button>
        )}
        {inv.status === "DRAFT" && (
          <Button variant="ghost" size="sm" onClick={onSend}>
            Envoyer
          </Button>
        )}
        {(inv.status === "SENT" || inv.status === "OVERDUE") && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onMarkPaid}
            className="text-success"
          >
            Marquer payée
          </Button>
        )}
        {inv.status !== "PAID" && inv.status !== "CANCELLED" && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onCancel}
            className="text-danger"
          >
            Annuler
          </Button>
        )}
      </div>
    </div>
  );
};

// ── Create Invoice Form ───────────────────────────────────────────────────────
const CreateForm: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const qc = useQueryClient();

  const [form, setForm] = useState<CreateInvoiceRequest>(() => {
    const now = Date.now();
    const today = new Date(now).toISOString().split("T")[0];
    const due30 = new Date(now + 30 * 86400000).toISOString().split("T")[0];
    return {
      clientName: "",
      clientEmail: "",
      number: `FAC-${now.toString().slice(-6)}`,
      amountHt: 0,
      vatRate: 20,
      currency: "EUR",
      issueDate: today,
      dueDate: due30,
      notes: "",
    };
  });

  const mutation = useMutation({
    mutationFn: () => invoicesApi.create(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["invoices"] });
      onClose();
    },
  });

  const ttc = form.amountHt * (1 + form.vatRate / 100);

  return (
    <Card>
      <CardHeader
        title="Nouvelle facture"
        helpTooltip={
          <HelpTooltip text="Créez une facture client avec calcul TVA automatique. Elle apparaît dans votre suivi des créances." />
        }
      />
      <div className="mt-5 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Client
            </label>
            <input
              className="fg-input"
              value={form.clientName}
              onChange={(e) => setForm({ ...form, clientName: e.target.value })}
              placeholder="Nom du client"
            />
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Email client
            </label>
            <input
              className="fg-input"
              type="email"
              value={form.clientEmail ?? ""}
              onChange={(e) =>
                setForm({ ...form, clientEmail: e.target.value })
              }
              placeholder="client@exemple.fr"
            />
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Numéro
            </label>
            <input
              className="fg-input"
              value={form.number}
              onChange={(e) => setForm({ ...form, number: e.target.value })}
            />
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Montant HT (€)
            </label>
            <input
              className="fg-input"
              type="number"
              min="0"
              step="0.01"
              value={form.amountHt}
              onChange={(e) =>
                setForm({ ...form, amountHt: parseFloat(e.target.value) || 0 })
              }
            />
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              TVA (%)
            </label>
            <select
              className="fg-input"
              value={form.vatRate}
              onChange={(e) =>
                setForm({ ...form, vatRate: parseFloat(e.target.value) })
              }
            >
              <option value={20}>20% (taux normal)</option>
              <option value={10}>10% (taux intermédiaire)</option>
              <option value={5.5}>5,5% (taux réduit)</option>
              <option value={0}>0% (exonéré)</option>
            </select>
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Montant TTC
            </label>
            <p className="fg-input bg-white/[0.02] text-primary font-semibold">
              {fmt(ttc)}
            </p>
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Date d&apos;émission
            </label>
            <input
              className="fg-input"
              type="date"
              value={form.issueDate}
              onChange={(e) => setForm({ ...form, issueDate: e.target.value })}
            />
          </div>
          <div>
            <label className="block text-xs text-text-secondary mb-1">
              Date d&apos;échéance
            </label>
            <input
              className="fg-input"
              type="date"
              value={form.dueDate}
              onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
            />
          </div>
        </div>
        <div>
          <label className="block text-xs text-text-secondary mb-1">
            Notes
          </label>
          <textarea
            className="fg-input min-h-[60px]"
            value={form.notes ?? ""}
            onChange={(e) => setForm({ ...form, notes: e.target.value })}
            placeholder="Conditions de paiement, mentions légales…"
          />
        </div>
        <div className="flex gap-3 justify-end">
          <Button variant="ghost" onClick={onClose}>
            Annuler
          </Button>
          <Button
            onClick={() => mutation.mutate()}
            disabled={
              mutation.isPending || !form.clientName || form.amountHt <= 0
            }
          >
            {mutation.isPending ? "Création…" : "Créer la facture"}
          </Button>
        </div>
      </div>
    </Card>
  );
};

// ── Main Page ─────────────────────────────────────────────────────────────────
const InvoicesPage: React.FC = () => {
  const [showForm, setShowForm] = useState(false);
  const qc = useQueryClient();

  const { data: invoices, isLoading } = useQuery({
    queryKey: ["invoices"],
    queryFn: invoicesApi.list,
  });

  const sendMut = useMutation({
    mutationFn: invoicesApi.send,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["invoices"] }),
  });
  const paidMut = useMutation({
    mutationFn: invoicesApi.markPaid,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["invoices"] }),
  });
  const cancelMut = useMutation({
    mutationFn: invoicesApi.cancel,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["invoices"] }),
  });
  const reminderMut = useMutation({
    mutationFn: invoicesApi.toggleReminder,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["invoices"] }),
  });

  const outstanding =
    invoices
      ?.filter((i) => i.status === "SENT" || i.status === "OVERDUE")
      .reduce((sum, i) => sum + i.totalTtc, 0) ?? 0;
  const overdue = invoices?.filter((i) => i.status === "OVERDUE").length ?? 0;

  return (
    <Layout title="Factures">
      <div className="max-w-5xl mx-auto space-y-6 animate-slide-up">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="page-title">Factures & Créances</h1>
            <p className="page-subtitle">
              Gérez votre facturation et suivez vos encaissements
            </p>
          </div>
          <Button onClick={() => setShowForm(true)} className="gap-2">
            <Plus size={16} /> Nouvelle facture
          </Button>
        </div>

        {showForm && <CreateForm onClose={() => setShowForm(false)} />}

        {/* KPI row */}
        <div className="grid grid-cols-3 gap-4">
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              En attente{" "}
              <HelpTooltip text="Montant total des factures envoyées non encore encaissées (statuts : envoyée ou en retard)." />
            </p>
            <p className="text-2xl font-bold font-numeric text-primary">
              {fmt(outstanding)}
            </p>
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              En retard{" "}
              <HelpTooltip text="Nombre de factures dont la date d'échéance est dépassée. Envoyez une relance pour accélérer l'encaissement." />
            </p>
            <p
              className={`text-2xl font-bold font-numeric ${overdue > 0 ? "text-danger" : "text-success"}`}
            >
              {overdue} {overdue === 1 ? "facture" : "factures"}
            </p>
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              Total émises{" "}
              <HelpTooltip text="Nombre total de factures créées, tous statuts confondus." />
            </p>
            <p className="text-2xl font-bold font-numeric text-white">
              {invoices?.length ?? 0}
            </p>
          </Card>
        </div>

        {/* Invoice list */}
        <Card>
          <CardHeader
            title="Toutes les factures"
            helpTooltip={
              <HelpTooltip text="Liste de toutes vos factures avec statuts, montants TTC et actions disponibles (envoyer, marquer payée, annuler)." />
            }
          />
          {isLoading ? (
            <Loader text="Chargement…" />
          ) : !invoices?.length ? (
            <div className="py-12 text-center text-text-muted">
              <FileText className="mx-auto mb-3 opacity-30" size={40} />
              <p>Aucune facture. Créez votre première facture.</p>
            </div>
          ) : (
            <div className="mt-4 space-y-2">
              {invoices.map((inv) => (
                <InvoiceRow
                  key={inv.id}
                  inv={inv}
                  onSend={() => sendMut.mutate(inv.id)}
                  onMarkPaid={() => paidMut.mutate(inv.id)}
                  onCancel={() => cancelMut.mutate(inv.id)}
                  onToggleReminder={() => reminderMut.mutate(inv.id)}
                />
              ))}
            </div>
          )}
        </Card>
      </div>
    </Layout>
  );
};

export default InvoicesPage;
