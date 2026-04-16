import React, { useState } from "react";
import { Link } from "react-router-dom";
import {
  TrendingUp,
  Zap,
  Shield,
  PieChart,
  Bell,
  FileText,
  Building2,
  Users,
  CheckCircle,
  ArrowRight,
  ChevronDown,
  ChevronUp,
  Globe,
  Brain,
  Target,
} from "lucide-react";
import { Button } from "@/components/ui/Button";

// ─── Feature card ──────────────────────────────────────────────────────────────
const FeatureCard: React.FC<{
  icon: React.ReactNode;
  title: string;
  desc: string;
  color: string;
}> = ({ icon, title, desc, color }) => (
  <div className="group p-6 bg-surface border border-white/[0.06] rounded-2xl hover:border-primary/30 hover:bg-surface-light transition-all duration-200 hover:-translate-y-0.5">
    <div
      className={`w-11 h-11 rounded-xl flex items-center justify-center mb-4 ${color}`}
    >
      {icon}
    </div>
    <h3 className="text-white font-semibold mb-2">{title}</h3>
    <p className="text-text-secondary text-sm leading-relaxed">{desc}</p>
  </div>
);

// ─── Pricing card ──────────────────────────────────────────────────────────────
const PricingCard: React.FC<{
  name: string;
  price: string;
  period: string;
  description: string;
  features: string[];
  cta: string;
  ctaLink: string;
  highlighted?: boolean;
  badge?: string;
}> = ({
  name,
  price,
  period,
  description,
  features,
  cta,
  ctaLink,
  highlighted,
  badge,
}) => (
  <div
    className={`relative flex flex-col p-8 rounded-2xl border transition-all duration-200 ${
      highlighted
        ? "bg-gradient-to-b from-primary/10 to-surface border-primary/40 shadow-glow"
        : "bg-surface border-white/[0.08] hover:border-white/20"
    }`}
  >
    {badge && (
      <span className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-1 rounded-full text-xs font-bold bg-gradient-primary text-white shadow-glow">
        {badge}
      </span>
    )}
    <p className="text-text-secondary text-sm font-medium mb-1">{name}</p>
    <div className="flex items-baseline gap-1 mb-2">
      <span className="text-4xl font-bold text-white font-numeric">{price}</span>
      <span className="text-text-secondary text-sm">{period}</span>
    </div>
    <p className="text-text-secondary text-sm mb-6">{description}</p>
    <ul className="space-y-3 mb-8 flex-1">
      {features.map((f) => (
        <li key={f} className="flex items-start gap-2.5 text-sm">
          <CheckCircle
            size={15}
            className="text-success flex-shrink-0 mt-0.5"
          />
          <span className="text-text-secondary">{f}</span>
        </li>
      ))}
    </ul>
    <Link to={ctaLink}>
      <Button
        variant={highlighted ? "gradient" : "outline"}
        size="lg"
        fullWidth
        rightIcon={<ArrowRight size={16} />}
      >
        {cta}
      </Button>
    </Link>
  </div>
);

// ─── FAQ item ──────────────────────────────────────────────────────────────────
const FaqItem: React.FC<{ q: string; a: string }> = ({ q, a }) => {
  const [open, setOpen] = useState(false);
  return (
    <div className="border-b border-white/[0.08]">
      <button
        className="w-full flex items-center justify-between py-5 text-left gap-4"
        onClick={() => setOpen((v) => !v)}
      >
        <span className="text-white font-medium">{q}</span>
        {open ? (
          <ChevronUp size={16} className="text-text-muted flex-shrink-0" />
        ) : (
          <ChevronDown size={16} className="text-text-muted flex-shrink-0" />
        )}
      </button>
      {open && (
        <p className="pb-5 text-text-secondary text-sm leading-relaxed">{a}</p>
      )}
    </div>
  );
};

// ─── Stat badge ────────────────────────────────────────────────────────────────
const StatBadge: React.FC<{ value: string; label: string }> = ({
  value,
  label,
}) => (
  <div className="text-center">
    <p className="text-3xl lg:text-4xl font-bold text-white font-numeric mb-1">
      {value}
    </p>
    <p className="text-text-secondary text-sm">{label}</p>
  </div>
);

// ─── Main component ────────────────────────────────────────────────────────────
const LandingPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-background text-white">
      {/* ── Navbar ───────────────────────────────────────────────────────────── */}
      <nav className="sticky top-0 z-50 border-b border-white/[0.06] bg-background/80 backdrop-blur-xl">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 flex items-center justify-between h-16">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-primary flex items-center justify-center shadow-glow">
              <Zap size={18} fill="white" className="text-white" />
            </div>
            <span
              className="font-bold text-white text-lg"
              style={{ fontFamily: "var(--font-display)" }}
            >
              FlowGuard
            </span>
          </div>
          <div className="hidden md:flex items-center gap-6 text-sm text-text-secondary">
            <a href="#features" className="hover:text-white transition">
              Fonctionnalités
            </a>
            <a href="#pricing" className="hover:text-white transition">
              Tarifs
            </a>
            <a href="#faq" className="hover:text-white transition">
              FAQ
            </a>
          </div>
          <div className="flex items-center gap-3">
            <Link to="/login">
              <Button variant="ghost" size="sm">
                Connexion
              </Button>
            </Link>
            <Link to="/register">
              <Button
                variant="gradient"
                size="sm"
                rightIcon={<ArrowRight size={14} />}
              >
                Essai gratuit
              </Button>
            </Link>
          </div>
        </div>
      </nav>

      {/* ── Hero ─────────────────────────────────────────────────────────────── */}
      <section className="relative overflow-hidden pt-20 pb-24 px-4 sm:px-6">
        {/* Glow backdrop */}
        <div
          className="absolute inset-0 pointer-events-none"
          style={{
            background:
              "radial-gradient(ellipse 80% 60% at 50% -10%, rgba(6,182,212,0.18) 0%, transparent 60%)",
          }}
        />
        <div className="relative max-w-4xl mx-auto text-center">
          <span className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-semibold mb-6">
            <Brain size={13} />
            Propulsé par l&apos;IA · Confiance bancaire
          </span>
          <h1
            className="text-4xl sm:text-5xl lg:text-6xl font-bold leading-tight mb-6"
            style={{ fontFamily: "var(--font-display)" }}
          >
            Pilotez votre trésorerie
            <br />
            <span className="bg-gradient-primary bg-clip-text text-transparent">
              avec l&apos;intelligence artificielle
            </span>
          </h1>
          <p className="text-text-secondary text-lg leading-relaxed max-w-2xl mx-auto mb-10">
            FlowGuard prédit vos flux de trésorerie 90 jours à l&apos;avance,
            détecte les risques avant qu&apos;ils surviennent, et débloque des
            financements instantanés quand vous en avez besoin — pour les
            particuliers comme pour les entreprises.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link to="/register">
              <Button
                variant="gradient"
                size="xl"
                rightIcon={<ArrowRight size={18} />}
              >
                Démarrer gratuitement
              </Button>
            </Link>
            <Link to="/register-business">
              <Button variant="outline" size="xl">
                Solution entreprise
              </Button>
            </Link>
          </div>
          <p className="text-text-muted text-xs mt-4">
            Aucune carte bancaire requise · Connexion bancaire sécurisée · RGPD
            compliant
          </p>
        </div>
      </section>

      {/* ── Stats ────────────────────────────────────────────────────────────── */}
      <section className="border-y border-white/[0.06] bg-surface/40 py-12 px-4 sm:px-6">
        <div className="max-w-4xl mx-auto grid grid-cols-2 lg:grid-cols-4 gap-8">
          <StatBadge value="92%" label="Précision des prévisions" />
          <StatBadge value="< 5 min" label="Décision de financement" />
          <StatBadge value="90 jours" label="Horizon de prévision" />
          <StatBadge value="100%" label="Sécurité des données" />
        </div>
      </section>

      {/* ── Features ─────────────────────────────────────────────────────────── */}
      <section id="features" className="py-20 px-4 sm:px-6">
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-14">
            <h2
              className="text-3xl lg:text-4xl font-bold text-white mb-4"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Tout ce dont vous avez besoin
            </h2>
            <p className="text-text-secondary text-lg max-w-xl mx-auto">
              Une plateforme complète pour comprendre, anticiper et optimiser
              votre situation financière.
            </p>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
            <FeatureCard
              icon={<TrendingUp size={22} className="text-primary" />}
              color="bg-primary/10"
              title="Prévisions IA 90 jours"
              desc="Modèle LSTM entraîné sur vos transactions réelles. Fourchette de confiance, points critiques, score de santé financière."
            />
            <FeatureCard
              icon={<Zap size={22} className="text-warning" />}
              color="bg-warning/10"
              title="Réserve FlashCredit"
              desc="Financement instantané jusqu'à 10 000 €. Décision automatique en moins de 5 minutes, sans garantie."
            />
            <FeatureCard
              icon={<Bell size={22} className="text-danger" />}
              color="bg-danger/10"
              title="Alertes intelligentes"
              desc="Détection d'anomalies en temps réel. Soyez notifié avant que votre solde passe dans le rouge."
            />
            <FeatureCard
              icon={<PieChart size={22} className="text-purple" />}
              color="bg-purple/10"
              title="Analyses de dépenses"
              desc="Catégorisation automatique par IA, tendances mensuelles, détection d'abonnements oubliés."
            />
            <FeatureCard
              icon={<Shield size={22} className="text-success" />}
              color="bg-success/10"
              title="Coffre fiscal"
              desc="Provisionnement automatique TVA, IS, charges. Jamais surpris par une échéance fiscale."
            />
            <FeatureCard
              icon={<Brain size={22} className="text-info" />}
              color="bg-info/10"
              title="Assistant IA"
              desc="Posez des questions en langage naturel sur vos finances et obtenez des conseils personnalisés instantanément."
            />
            <FeatureCard
              icon={<FileText size={22} className="text-primary" />}
              color="bg-primary/10"
              title="Facturation intégrée"
              desc="Créez et envoyez des factures, suivez les encaissements, exportez vers votre comptable en un clic."
            />
            <FeatureCard
              icon={<Globe size={22} className="text-success" />}
              color="bg-success/10"
              title="Open Banking"
              desc="Connexion sécurisée à plus de 2 000 banques européennes via Nordigen & Swan. Synchronisation en temps réel."
            />
            <FeatureCard
              icon={<Target size={22} className="text-warning" />}
              color="bg-warning/10"
              title="Simulateur de scénarios"
              desc="Simulez l'impact d'un retard de paiement, d'une dépense exceptionnelle ou d'une rentrée anticipée."
            />
          </div>
        </div>
      </section>

      {/* ── Who is it for ─────────────────────────────────────────────────────── */}
      <section className="py-20 px-4 sm:px-6 bg-surface/30">
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-14">
            <h2
              className="text-3xl lg:text-4xl font-bold text-white mb-4"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Pour tout le monde
            </h2>
            <p className="text-text-secondary text-lg">
              Que vous gériez un budget personnel ou une trésorerie
              d&apos;entreprise.
            </p>
          </div>
          <div className="grid md:grid-cols-2 gap-8">
            {/* Individual */}
            <div className="p-8 bg-surface border border-white/[0.08] rounded-2xl hover:border-primary/20 transition-all">
              <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center mb-5">
                <Users size={24} className="text-primary" />
              </div>
              <h3
                className="text-xl font-bold text-white mb-3"
                style={{ fontFamily: "var(--font-display)" }}
              >
                Particuliers &amp; Freelances
              </h3>
              <p className="text-text-secondary mb-5 text-sm leading-relaxed">
                Anticipez vos fins de mois, optimisez vos dépenses,
                provisionnez vos charges fiscales et accédez à un financement si
                nécessaire.
              </p>
              <ul className="space-y-2 mb-6">
                {[
                  "Prévisions de solde à 30 jours",
                  "Détection d'abonnements superflus",
                  "Coffre TVA automatique (auto-entrepreneurs)",
                  "Réserve de trésorerie jusqu'à 10 000 €",
                  "Alertes avant dépassement de découvert",
                ].map((item) => (
                  <li key={item} className="flex items-center gap-2.5 text-sm">
                    <CheckCircle
                      size={14}
                      className="text-success flex-shrink-0"
                    />
                    <span className="text-text-secondary">{item}</span>
                  </li>
                ))}
              </ul>
              <Link to="/register">
                <Button
                  variant="outline"
                  fullWidth
                  rightIcon={<ArrowRight size={15} />}
                >
                  Créer un compte particulier
                </Button>
              </Link>
            </div>

            {/* Business */}
            <div className="p-8 bg-gradient-to-b from-primary/[0.07] to-surface border border-primary/25 rounded-2xl shadow-glow">
              <div className="w-12 h-12 rounded-2xl bg-primary/15 flex items-center justify-center mb-5">
                <Building2 size={24} className="text-primary" />
              </div>
              <h3
                className="text-xl font-bold text-white mb-3"
                style={{ fontFamily: "var(--font-display)" }}
              >
                TPE · PME · Startups
              </h3>
              <p className="text-text-secondary mb-5 text-sm leading-relaxed">
                Donnez à votre DAF les outils d&apos;un grand groupe : prévisions
                à 90 jours, benchmarking sectoriel, comptabilité simplifiée et
                accès au financement en quelques minutes.
              </p>
              <ul className="space-y-2 mb-6">
                {[
                  "Prévisions IA à 90 jours avec intervalles de confiance",
                  "Benchmarking vs. votre secteur d'activité",
                  "Gestion d'équipe et accès comptable",
                  "Export FEC & portail expert-comptable",
                  "Simulateur de scénarios (retard client, dépense imprévue)",
                  "Réserve de trésorerie express jusqu'à 10 000 €",
                ].map((item) => (
                  <li key={item} className="flex items-center gap-2.5 text-sm">
                    <CheckCircle
                      size={14}
                      className="text-success flex-shrink-0"
                    />
                    <span className="text-text-secondary">{item}</span>
                  </li>
                ))}
              </ul>
              <Link to="/register-business">
                <Button
                  variant="gradient"
                  fullWidth
                  rightIcon={<ArrowRight size={15} />}
                >
                  Créer un compte entreprise
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ── Pricing ───────────────────────────────────────────────────────────── */}
      <section id="pricing" className="py-20 px-4 sm:px-6">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-14">
            <h2
              className="text-3xl lg:text-4xl font-bold text-white mb-4"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Tarifs transparents
            </h2>
            <p className="text-text-secondary text-lg">
              Commencez gratuitement, évoluez selon vos besoins.
            </p>
          </div>
          <div className="grid md:grid-cols-3 gap-6">
            <PricingCard
              name="Free"
              price="0 €"
              period="/ mois"
              description="Pour démarrer et explorer"
              features={[
                "Connexion 1 banque",
                "Prévisions à 30 jours",
                "Alertes de base",
                "Analyse des dépenses",
                "1 Réserve FlashCredit / an",
              ]}
              cta="Commencer gratuitement"
              ctaLink="/register"
            />
            <PricingCard
              name="Pro"
              price="19 €"
              period="/ mois"
              description="Pour les freelances et petites équipes"
              features={[
                "Connexion illimitée de banques",
                "Prévisions à 90 jours",
                "Alertes avancées & anomalies",
                "Coffre fiscal automatique",
                "Facturation intégrée",
                "Simulateur de scénarios",
                "Export CSV & PDF",
                "Réserves FlashCredit illimitées",
              ]}
              cta="Démarrer l'essai Pro"
              ctaLink="/register"
              highlighted
              badge="Le plus populaire"
            />
            <PricingCard
              name="Scale"
              price="49 €"
              period="/ mois"
              description="Pour les PME avec équipes"
              features={[
                "Tout le plan Pro",
                "Gestion d'équipe (jusqu'à 10 membres)",
                "Portail expert-comptable",
                "Export FEC",
                "Benchmarking sectoriel",
                "API access",
                "Support prioritaire",
              ]}
              cta="Contacter les ventes"
              ctaLink="/register-business"
            />
          </div>
        </div>
      </section>

      {/* ── FAQ ──────────────────────────────────────────────────────────────── */}
      <section id="faq" className="py-20 px-4 sm:px-6 bg-surface/30">
        <div className="max-w-2xl mx-auto">
          <h2
            className="text-3xl font-bold text-white mb-10 text-center"
            style={{ fontFamily: "var(--font-display)" }}
          >
            Questions fréquentes
          </h2>
          <div>
            {[
              {
                q: "Comment FlowGuard accède-t-il à mes données bancaires ?",
                a: "FlowGuard utilise l'Open Banking réglementé (DSP2) via des partenaires agréés (Nordigen, Swan). Vous autorisez la lecture seule de vos transactions — nous ne stockons jamais vos identifiants bancaires.",
              },
              {
                q: "Le modèle d'IA est-il fiable ?",
                a: "Notre modèle LSTM est ré-entraîné chaque nuit sur vos propres données. Il affiche un score de confiance et des intervalles de prévision honnêtes — jamais une fausse certitude. La précision moyenne est de 92 % sur 30 jours.",
              },
              {
                q: "Comment fonctionne la Réserve FlashCredit ?",
                a: "En cas de tension de trésorerie détectée, vous pouvez demander une réserve de 500 € à 10 000 €. La décision est automatique (< 5 min), sans garantie, avec une commission fixe de 1,5 %. Vous disposez de 14 jours pour vous rétracter.",
              },
              {
                q: "Mes données sont-elles en sécurité ?",
                a: "Vos données sont chiffrées en transit (TLS 1.3) et au repos (AES-256). FlowGuard est hébergé dans des datacenters européens certifiés ISO 27001, en conformité totale avec le RGPD.",
              },
              {
                q: "Puis-je utiliser FlowGuard sans compte bancaire connecté ?",
                a: "Oui. Vous pouvez importer des relevés de compte en CSV ou saisir manuellement vos transactions. Les prévisions seront actives dès que vous aurez 7 jours d'historique.",
              },
            ].map((item) => (
              <FaqItem key={item.q} q={item.q} a={item.a} />
            ))}
          </div>
        </div>
      </section>

      {/* ── Final CTA ────────────────────────────────────────────────────────── */}
      <section className="py-20 px-4 sm:px-6">
        <div className="max-w-3xl mx-auto text-center">
          <div
            className="p-10 rounded-3xl border border-primary/20 relative overflow-hidden"
            style={{
              background:
                "radial-gradient(ellipse at top, rgba(6,182,212,0.12) 0%, #111C44 60%)",
            }}
          >
            <h2
              className="text-3xl lg:text-4xl font-bold text-white mb-4"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Prêt à voir l&apos;avenir de votre trésorerie ?
            </h2>
            <p className="text-text-secondary text-lg mb-8">
              Rejoignez des milliers d&apos;entreprises et de particuliers qui
              pilotent leurs finances avec FlowGuard.
            </p>
            <div className="flex flex-col sm:flex-row justify-center gap-4">
              <Link to="/register">
                <Button
                  variant="gradient"
                  size="xl"
                  rightIcon={<ArrowRight size={18} />}
                >
                  Créer un compte gratuit
                </Button>
              </Link>
              <Link to="/login">
                <Button variant="ghost" size="xl">
                  J&apos;ai déjà un compte
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ── Footer ───────────────────────────────────────────────────────────── */}
      <footer className="border-t border-white/[0.06] py-10 px-4 sm:px-6">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-gradient-primary flex items-center justify-center">
              <Zap size={15} fill="white" className="text-white" />
            </div>
            <span className="text-white font-bold">FlowGuard</span>
          </div>
          <div className="flex flex-wrap justify-center gap-6 text-sm text-text-muted">
            <a href="#features" className="hover:text-white transition">
              Fonctionnalités
            </a>
            <a href="#pricing" className="hover:text-white transition">
              Tarifs
            </a>
            <a href="#faq" className="hover:text-white transition">
              FAQ
            </a>
            <Link to="/login" className="hover:text-white transition">
              Connexion
            </Link>
          </div>
          <p className="text-text-muted text-sm">
            © {new Date().getFullYear()} FlowGuard · RGPD · DSP2
          </p>
        </div>
      </footer>
    </div>
  );
};

export default LandingPage;
