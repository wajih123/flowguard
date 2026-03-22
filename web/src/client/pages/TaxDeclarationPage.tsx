import React, { useState } from "react";
import {
  FileText,
  RefreshCw,
  Copy,
  CheckCircle,
  AlertTriangle,
  Info,
  ChevronDown,
  ChevronUp,
  TrendingUp,
  Calculator,
  Landmark,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery } from "@tanstack/react-query";
import { taxDeclarationApi } from "@/api/taxDeclaration";
import type { TaxDeclaration } from "@/api/taxDeclaration";

// ─── Helpers ─────────────────────────────────────────────────────────────────

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

const REGIME_LABELS: Record<string, string> = {
  MICRO_BNC: "Micro-BNC (prestations / freelance)",
  MICRO_BIC: "Micro-BIC (ventes / services)",
  REEL_SIMPLIFIE: "Régime réel simplifié",
};

const BOX_META: Record<string, { form: string; label: string; color: string }> =
  {
    "5HQ": {
      form: "2042-C Pro",
      label: "Recettes brutes BNC (prestations)",
      color: "text-primary",
    },
    "5KO": {
      form: "2042-C Pro",
      label: "CA BIC services / hébergement",
      color: "text-primary",
    },
    "5KC": {
      form: "2042-C Pro",
      label: "CA BIC ventes",
      color: "text-primary",
    },
    "5QC": {
      form: "2042-C Pro",
      label: "Bénéfice BNC réel (à reporter)",
      color: "text-success",
    },
    "5DF": {
      form: "2042-C Pro",
      label: "Bénéfice BIC réel (à reporter)",
      color: "text-success",
    },
    CA3_0979: {
      form: "CA3 / CA12",
      label: "TVA collectée — base taux normal (ligne 0979)",
      color: "text-warning",
    },
    CA3_0703: {
      form: "CA3 / CA12",
      label: "TVA déductible sur achats (ligne 0703)",
      color: "text-warning",
    },
    CA3_0705: {
      form: "CA3 / CA12",
      label: "TVA nette à payer (ligne 0705)",
      color: "text-danger",
    },
  };

// ─── Sub-components ──────────────────────────────────────────────────────────

const CopyButton: React.FC<{ value: number }> = ({ value }) => {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard.writeText(String(Math.round(value)));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button
      onClick={copy}
      className={`flex items-center gap-1 px-2 py-1 rounded text-xs font-medium transition ${
        copied
          ? "bg-success/15 text-success"
          : "bg-white/[0.05] text-text-muted hover:text-white"
      }`}
      title="Copier la valeur"
    >
      {copied ? <CheckCircle size={11} /> : <Copy size={11} />}
      {copied ? "Copié" : "Copier"}
    </button>
  );
};

const BoxRow: React.FC<{ code: string; value: number }> = ({ code, value }) => {
  const meta = BOX_META[code] ?? { form: "", label: code, color: "text-white" };
  return (
    <div className="flex items-center gap-3 px-4 py-3 border-b border-white/[0.04] last:border-0">
      <div className="flex-shrink-0 w-16">
        <span
          className={`font-mono text-sm font-bold px-1.5 py-0.5 rounded bg-white/[0.05] ${meta.color}`}
        >
          {code}
        </span>
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-white text-sm truncate">{meta.label}</p>
        <p className="text-text-muted text-xs">{meta.form}</p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <p className="font-numeric font-semibold text-white text-sm">
          {fmt(value)}
        </p>
        <CopyButton value={value} />
      </div>
    </div>
  );
};

const ChargeRow: React.FC<{ label: string; value: number; help?: string }> = ({
  label,
  value,
  help,
}) => (
  <div className="flex items-center justify-between gap-3 px-4 py-2.5 border-b border-white/[0.04] last:border-0">
    <div className="flex items-center gap-1.5">
      <p className="text-text-secondary text-sm">{label}</p>
      {help && <HelpTooltip text={help} />}
    </div>
    <div className="flex items-center gap-2">
      <p className="font-numeric text-white text-sm">{fmt(value)}</p>
      <CopyButton value={value} />
    </div>
  </div>
);

const SectionToggle: React.FC<{
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  defaultOpen?: boolean;
}> = ({ title, icon, children, defaultOpen = true }) => {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <Card padding="none">
      <button
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-5 py-4"
      >
        <div className="flex items-center gap-2 text-white font-semibold text-sm">
          {icon}
          {title}
        </div>
        {open ? (
          <ChevronUp size={15} className="text-text-muted" />
        ) : (
          <ChevronDown size={15} className="text-text-muted" />
        )}
      </button>
      {open && <div className="border-t border-white/[0.05]">{children}</div>}
    </Card>
  );
};

// ─── Main page ────────────────────────────────────────────────────────────────

const TaxDeclarationPage: React.FC = () => {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear - 1);

  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ["tax-declaration", year],
    queryFn: () => taxDeclarationApi.get(year),
    staleTime: 5 * 60 * 1000,
  });

  const yearOptions = [currentYear - 1, currentYear - 2, currentYear - 3];

  return (
    <Layout title="Déclaration d'impôts">
      <div className="max-w-3xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between flex-wrap gap-3">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Aide à la déclaration d&rsquo;impôts
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Valeurs pré-calculées à copier dans impots.gouv.fr ou à remettre à
              votre expert-comptable
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="flex ring-1 ring-white/[0.1] rounded-lg overflow-hidden">
              {yearOptions.map((y) => (
                <button
                  key={y}
                  onClick={() => setYear(y)}
                  className={`px-3 py-1.5 text-sm font-medium transition ${
                    year === y
                      ? "bg-primary text-white"
                      : "bg-white/[0.03] text-text-muted hover:text-white"
                  }`}
                >
                  {y}
                </button>
              ))}
            </div>
            <button
              onClick={() => refetch()}
              className="p-2 rounded-lg text-text-muted hover:text-white hover:bg-white/[0.05] transition"
              title="Actualiser"
            >
              <RefreshCw
                size={16}
                className={isFetching ? "animate-spin" : ""}
              />
            </button>
          </div>
        </div>

        {/* Legal disclaimer banner */}
        <div className="flex items-start gap-3 p-4 rounded-xl border border-primary/20 bg-primary/5">
          <Info size={16} className="text-primary mt-0.5 shrink-0" />
          <p className="text-text-secondary text-xs leading-relaxed">
            <span className="text-white font-medium">
              Document préparatoire uniquement.
            </span>{" "}
            FlowGuard ne dépose aucune déclaration à votre place. Les montants
            ci-dessous sont calculés à partir de vos factures et transactions —
            vérifiez-les avec votre expert-comptable avant tout dépôt officiel.
          </p>
        </div>

        {isLoading ? (
          <Card padding="lg">
            <Loader text="Calcul de votre déclaration…" />
          </Card>
        ) : !data ? (
          <Card padding="lg">
            <div className="flex flex-col items-center py-8 gap-3 text-text-muted">
              <FileText size={32} className="opacity-30" />
              <p className="text-sm">Aucune donnée disponible pour {year}</p>
            </div>
          </Card>
        ) : (
          <>
            {/* Regime + summary KPIs */}
            <Card padding="lg">
              <div className="flex items-center justify-between flex-wrap gap-2 mb-4">
                <div>
                  <p className="text-text-muted text-xs mb-1">
                    Régime fiscal détecté
                  </p>
                  <p className="text-white font-semibold text-sm">
                    {REGIME_LABELS[data.regime] ?? data.regime}
                  </p>
                </div>
                <span className="text-2xs text-text-muted px-2 py-1 bg-white/[0.04] rounded-full">
                  Exercice {year}
                </span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <KpiBox
                  label="CA brut HT"
                  value={data.grossRevenue}
                  color="text-white"
                />
                <KpiBox
                  label={
                    data.regime === "REEL_SIMPLIFIE"
                      ? "Bénéfice net"
                      : "Revenu imposable"
                  }
                  value={
                    data.regime === "REEL_SIMPLIFIE"
                      ? data.beneficeNet
                      : data.taxableIncome
                  }
                  color="text-success"
                />
                <KpiBox
                  label="TVA collectée"
                  value={data.tvaCollectee}
                  color="text-warning"
                />
                <KpiBox
                  label="TVA à payer"
                  value={data.tvaSolde}
                  color="text-danger"
                />
              </div>
            </Card>

            {/* Warnings */}
            {data.warnings.length > 0 && (
              <div className="space-y-2">
                {data.warnings.map((w, i) => (
                  <div
                    key={i}
                    className={`flex items-start gap-2.5 px-4 py-3 rounded-xl border text-xs ${
                      i === data.warnings.length - 1
                        ? "border-white/[0.08] bg-white/[0.02] text-text-muted"
                        : "border-warning/20 bg-warning/5 text-warning"
                    }`}
                  >
                    <AlertTriangle size={12} className="mt-0.5 shrink-0" />
                    {w}
                  </div>
                ))}
              </div>
            )}

            {/* Form boxes — the core copy-paste section */}
            <SectionToggle
              title="Cases formulaires à reporter"
              icon={<FileText size={15} />}
              defaultOpen
            >
              <CardHeader
                title=""
                helpTooltip={
                  <HelpTooltip text="Codes officiels des cases de la déclaration 2042-C Pro et CA3. Copiez chaque valeur directement dans le formulaire correspondant sur impots.gouv.fr." />
                }
              />
              {Object.entries(data.formBoxes).map(([code, value]) => (
                <BoxRow key={code} code={code} value={value} />
              ))}
            </SectionToggle>

            {/* Micro regime breakdown */}
            {(data.regime === "MICRO_BNC" || data.regime === "MICRO_BIC") && (
              <SectionToggle
                title="Détail micro-régime"
                icon={<Calculator size={15} />}
                defaultOpen
              >
                <div className="px-4 py-3 space-y-2 text-sm">
                  <KvRow
                    label="CA brut HT"
                    value={fmt(data.grossRevenue)}
                    help="Le chiffre d'affaires encaissé sur l'année"
                  />
                  <KvRow
                    label={`Abattement forfaitaire (${data.regime === "MICRO_BIC" ? "50%" : "34%"})`}
                    value={`− ${fmt(data.abattement)}`}
                    valueClass="text-danger"
                    help={
                      data.regime === "MICRO_BIC"
                        ? "Abattement BIC services 50% — appliqué automatiquement par l'administration"
                        : "Abattement BNC 34% — appliqué automatiquement par l'administration"
                    }
                  />
                  <div className="h-px bg-white/[0.07] my-1" />
                  <KvRow
                    label="Revenu imposable"
                    value={fmt(data.taxableIncome)}
                    valueClass="text-success font-semibold"
                    help="Montant soumis au barème de l'impôt sur le revenu"
                  />
                </div>
              </SectionToggle>
            )}

            {/* Charges detail (réel or informational) */}
            <SectionToggle
              title={
                data.regime === "REEL_SIMPLIFIE"
                  ? "Charges déductibles (régime réel)"
                  : "Charges détectées (à titre informatif)"
              }
              icon={<TrendingUp size={15} />}
              defaultOpen={data.regime === "REEL_SIMPLIFIE"}
            >
              {data.regime !== "REEL_SIMPLIFIE" && (
                <div className="px-4 pt-3 pb-2">
                  <p className="text-text-muted text-xs">
                    En micro-régime, les charges réelles ne sont pas déductibles
                    (l&rsquo;abattement forfaitaire les remplace). Ces montants
                    sont affichés pour information et pour vous aider à choisir
                    votre régime.
                  </p>
                </div>
              )}
              <ChargeRow
                label="Loyer / local professionnel"
                value={data.chargesLoyer}
              />
              <ChargeRow
                label="Télécom / Internet"
                value={data.chargesTelecom}
              />
              <ChargeRow
                label="Assurances professionnelles"
                value={data.chargesAssurance}
              />
              <ChargeRow
                label="Transport / déplacements"
                value={data.chargesTransport}
              />
              <ChargeRow
                label="Abonnements logiciels / SaaS"
                value={data.chargesAbonnements}
              />
              <ChargeRow label="Énergie / eau" value={data.chargesEnergie} />
              <ChargeRow
                label="Fournisseurs / sous-traitants"
                value={data.chargesFournisseurs}
              />
              <ChargeRow label="Autres charges" value={data.chargesAutres} />
              <div className="flex items-center justify-between gap-3 px-4 py-3 bg-white/[0.03]">
                <p className="text-white text-sm font-semibold">
                  Total charges
                </p>
                <div className="flex items-center gap-2">
                  <p className="font-numeric font-bold text-white">
                    {fmt(data.totalCharges)}
                  </p>
                  <CopyButton value={data.totalCharges} />
                </div>
              </div>
              {data.regime === "REEL_SIMPLIFIE" && (
                <div className="flex items-center justify-between gap-3 px-4 py-3 bg-success/5 border-t border-success/10">
                  <p className="text-success text-sm font-semibold">
                    Bénéfice net
                  </p>
                  <div className="flex items-center gap-2">
                    <p className="font-numeric font-bold text-success">
                      {fmt(data.beneficeNet)}
                    </p>
                    <CopyButton value={data.beneficeNet} />
                  </div>
                </div>
              )}
            </SectionToggle>

            {/* TVA detail */}
            <SectionToggle
              title="Déclaration TVA (CA3 / CA12)"
              icon={<Landmark size={15} />}
            >
              <ChargeRow
                label="TVA collectée sur vos ventes"
                value={data.tvaCollectee}
                help="20% de votre CA HT. À reporter case 0979 du CA3."
              />
              <ChargeRow
                label="TVA déductible sur vos achats"
                value={data.tvaDeductible}
                help="TVA estimée sur vos charges professionnelles. À reporter case 0703."
              />
              <div className="flex items-center justify-between gap-3 px-4 py-3 bg-white/[0.03]">
                <p className="text-white text-sm font-semibold">
                  TVA nette à payer
                </p>
                <div className="flex items-center gap-2">
                  <p className="font-numeric font-bold text-danger">
                    {fmt(data.tvaSolde)}
                  </p>
                  <CopyButton value={data.tvaSolde} />
                </div>
              </div>
              <div className="px-4 py-3">
                <p className="text-text-muted text-xs">
                  Si vous êtes en franchise de TVA (CA &lt; 36 800 € BNC, &lt;
                  91 900 € BIC), aucune déclaration TVA n&rsquo;est requise.
                  Vérifiez votre statut.
                </p>
              </div>
            </SectionToggle>

            {/* Invoice stats */}
            <Card padding="md">
              <div className="flex items-center gap-2 mb-3">
                <FileText size={14} className="text-text-muted" />
                <p className="text-text-muted text-xs font-medium uppercase tracking-wide">
                  Résumé des factures {year}
                </p>
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div className="text-center">
                  <p className="font-numeric text-white text-lg font-bold">
                    {data.totalInvoices}
                  </p>
                  <p className="text-text-muted text-xs">Total factures</p>
                </div>
                <div className="text-center">
                  <p className="font-numeric text-success text-lg font-bold">
                    {data.paidInvoices}
                  </p>
                  <p className="text-text-muted text-xs">Encaissées</p>
                </div>
                <div className="text-center">
                  <p
                    className={`font-numeric text-lg font-bold ${
                      data.uncategorizedTransactions > 0
                        ? "text-warning"
                        : "text-success"
                    }`}
                  >
                    {data.uncategorizedTransactions}
                  </p>
                  <p className="text-text-muted text-xs">Tx non catégorisées</p>
                </div>
              </div>
            </Card>
          </>
        )}
      </div>
    </Layout>
  );
};

// ─── Small helpers ────────────────────────────────────────────────────────────

const KpiBox: React.FC<{ label: string; value: number; color: string }> = ({
  label,
  value,
  color,
}) => (
  <div className="rounded-lg bg-white/[0.03] border border-white/[0.06] p-3">
    <p className="text-text-muted text-2xs mb-1">{label}</p>
    <p className={`font-numeric font-bold text-base ${color}`}>{fmt(value)}</p>
  </div>
);

const KvRow: React.FC<{
  label: string;
  value: string;
  help?: string;
  valueClass?: string;
}> = ({ label, value, help, valueClass = "text-white" }) => (
  <div className="flex items-center justify-between gap-3">
    <div className="flex items-center gap-1.5">
      <p className="text-text-secondary text-sm">{label}</p>
      {help && <HelpTooltip text={help} />}
    </div>
    <p className={`font-numeric text-sm ${valueClass}`}>{value}</p>
  </div>
);

export default TaxDeclarationPage;
