import React, { useState, useRef, useEffect, useCallback } from "react";
import {
  Brain,
  Send,
  TrendingUp,
  PieChart,
  Zap,
  AlertTriangle,
  Target,
  RefreshCw,
  Sparkles,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { useDashboard } from "@/hooks/useDashboard";
import { useForecast } from "@/hooks/useForecast";
import { useAuthStore } from "@/store/authStore";

// ─── Types ─────────────────────────────────────────────────────────────────────
type MessageRole = "user" | "assistant";

interface Message {
  id: string;
  role: MessageRole;
  text: string;
  timestamp: Date;
}

interface QuickQuestion {
  label: string;
  icon: React.ReactNode;
  question: string;
  color: string;
}

// ─── Helpers ───────────────────────────────────────────────────────────────────
const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

/** Generate a personalised AI response based on question and current user context */
function generateAnswer(
  question: string,
  context: {
    balance: number;
    balance30d: number;
    trend: number;
    healthScore: number;
    hasAlert: boolean;
    alertMessage?: string;
    criticalPoints: number;
    confidenceScore: number;
    firstName: string;
  },
): string {
  const q = question.toLowerCase();
  const { balance, balance30d, trend, healthScore, criticalPoints, firstName } =
    context;
  const name = firstName ? `, ${firstName}` : "";

  // ── Liquidity / cash flow questions
  if (
    q.includes("solde") ||
    q.includes("trésorerie") ||
    q.includes("argent") ||
    q.includes("cash")
  ) {
    const trendLabel = trend >= 0 ? "en hausse" : "en baisse";
    const sign = trend >= 0 ? "+" : "";
    return [
      `Votre solde actuel est de **${fmt(balance)}**${name}.`,
      `Sur les 30 prochains jours, il est prévu ${trendLabel} de **${sign}${fmt(trend)}**, pour atteindre **${fmt(balance30d)}**.`,
      healthScore >= 70
        ? "✅ Votre santé financière est **bonne**. Continuez sur cette lancée !"
        : healthScore >= 40
          ? "⚠️ Votre santé financière est **moyenne**. Surveillez vos dépenses récurrentes."
          : "🔴 Votre santé financière est **fragile**. Envisagez d'activer la Réserve FlashCredit si un déficit est imminent.",
      criticalPoints > 0
        ? `⚡ **${criticalPoints} point${criticalPoints > 1 ? "s" : ""} de vigilance** détecté${criticalPoints > 1 ? "s" : ""} dans la période. Consultez la page **Prévisions** pour le détail.`
        : "",
    ]
      .filter(Boolean)
      .join("\n\n");
  }

  // ── Forecast / prediction
  if (
    q.includes("prévis") ||
    q.includes("forecast") ||
    q.includes("futur") ||
    q.includes("avenir")
  ) {
    return [
      `Notre modèle LSTM analyse vos transactions historiques et prédit votre solde sur les prochains jours.`,
      `**Solde actuel :** ${fmt(balance)}`,
      `**Solde prévu à 30 jours :** ${fmt(balance30d)} (${trend >= 0 ? "+" : ""}${fmt(trend)})`,
      `**Score de santé :** ${healthScore}/100`,
      criticalPoints > 0
        ? `⚠️ **${criticalPoints} point${criticalPoints > 1 ? "s" : ""} critique${criticalPoints > 1 ? "s" : ""}** détecté${criticalPoints > 1 ? "s" : ""}. Consultez la page **Prévisions** pour voir les dates et activer une Réserve si nécessaire.`
        : "✅ Aucun déficit prévu sur la période analysée.",
      `\nPour modifier l'horizon (60j ou 90j) passez en **Plan Pro**.`,
    ]
      .filter(Boolean)
      .join("\n\n");
  }

  // ── Alerts / risks
  if (
    q.includes("alerte") ||
    q.includes("risque") ||
    q.includes("danger") ||
    q.includes("problème")
  ) {
    if (context.hasAlert && context.alertMessage) {
      return [
        `🔴 **Alerte active :** ${context.alertMessage}`,
        `Votre solde est actuellement de **${fmt(balance)}**. Une action rapide est recommandée.`,
        "**Options disponibles :**",
        "• Activez la **Réserve FlashCredit** pour couvrir le déficit (réponse < 5 min)",
        "• Anticipez une facture client via la page **Factures**",
        "• Consultez vos **Scénarios** pour simuler différentes options",
      ].join("\n\n");
    }
    return [
      `✅ **Aucune alerte critique** en ce moment${name}.`,
      `Votre solde de **${fmt(balance)}** est stable.`,
      "Je surveille en permanence vos flux. Si une anomalie est détectée, vous serez notifié immédiatement.",
    ].join("\n\n");
  }

  // ── Savings / improvement
  if (
    q.includes("économ") ||
    q.includes("épargn") ||
    q.includes("améliorer") ||
    q.includes("optimis") ||
    q.includes("conseil")
  ) {
    return [
      `Voici mes **3 conseils personnalisés** pour optimiser votre trésorerie${name} :`,
      `\n**1. Détectez les abonnements oubliés**\nConsultez la page **Abonnements** — les services inactifs représentent en moyenne 15-20% des dépenses récurrentes.`,
      `\n**2. Provisionnez vos charges fiscales**\nActivez le **Coffre Fiscal** pour mettre de côté automatiquement TVA et charges. Évitez les mauvaises surprises en fin de trimestre.`,
      `\n**3. Anticipez votre besoin de trésorerie**\nAvec ${criticalPoints > 0 ? `**${criticalPoints} point${criticalPoints > 1 ? "s" : ""} de vigilance** détecté${criticalPoints > 1 ? "s" : ""}` : "**aucune tension prévisionnelle**"}, ${criticalPoints > 0 ? "activez la Réserve avant d'être en difficulté" : "c'est le bon moment pour constituer une épargne de précaution"}.`,
    ].join("\n");
  }

  // ── Flash credit
  if (
    q.includes("crédit") ||
    q.includes("financement") ||
    q.includes("emprunt") ||
    q.includes("réserve") ||
    q.includes("prêt")
  ) {
    return [
      `La **Réserve FlashCredit** permet d'obtenir un financement instantané de **500 € à 10 000 €**.`,
      `\n**Conditions :**`,
      `• Commission fixe de **1,5%** (affichée en €, jamais en %)`,
      `• Réponse automatique en **moins de 5 minutes**`,
      `• **Sans garantie** — basé sur votre historique de flux`,
      `• Droit de rétractation de **14 jours** (Art. L312-19)`,
      `\n**Votre éligibilité :** ${healthScore >= 50 ? "✅ Profil éligible — votre score de santé est suffisant." : "⚠️ Score de santé insuffisant pour l'instant. Stabilisez vos flux sur 30 jours."}`,
      `\nRendez-vous sur la page **Réserve** pour faire une demande.`,
    ].join("\n\n");
  }

  // ── Budget
  if (
    q.includes("budget") ||
    q.includes("dépens") ||
    q.includes("catégor")
  ) {
    return [
      `Pour analyser vos **dépenses par catégorie**, consultez la page **Analyses**.`,
      `\nJe détecte automatiquement :`,
      `• 🏠 Loyer & charges`,
      `• 🚗 Transport`,
      `• 🛒 Alimentation`,
      `• 📱 Abonnements (téléphone, SaaS, streaming...)`,
      `• ⚡ Énergie`,
      `• 💼 Fournisseurs & clients`,
      `\nVotre solde actuel est de **${fmt(balance)}**. Définissez un budget par catégorie dans **Budget** pour suivre vos objectifs.`,
    ].join("\n\n");
  }

  // ── Health score
  if (
    q.includes("santé") ||
    q.includes("score") ||
    q.includes("note") ||
    q.includes("évaluation")
  ) {
    const level =
      healthScore >= 70 ? "Bonne" : healthScore >= 40 ? "Moyenne" : "Fragile";
    const color = healthScore >= 70 ? "✅" : healthScore >= 40 ? "⚠️" : "🔴";
    return [
      `${color} Votre **Score de Santé Financière** est de **${healthScore}/100** — niveau *${level}*.`,
      `\nCe score est calculé à partir de :`,
      `• Votre solde actuel (**${fmt(balance)}**)`,
      `• La tendance à 30 jours (**${trend >= 0 ? "+" : ""}${fmt(trend)}**)`,
      `• Le nombre de points critiques (**${criticalPoints}**)`,
      `• La régularité de vos revenus`,
      `\n**Comment l'améliorer ?**`,
      healthScore < 70
        ? [
            "• Réduisez les dépenses récurrentes non essentielles",
            "• Assurez-vous d'encaisser toutes vos factures à temps",
            "• Activez le Coffre Fiscal pour provisionner les charges",
          ].join("\n")
        : "Continuez à maintenir un solde positif et à anticiper vos dépenses !",
    ].join("\n\n");
  }

  // ── Default / catch-all
  return [
    `Bonne question${name} ! Je suis votre **Conseiller Financier IA FlowGuard**.`,
    `\nVoici un résumé de votre situation :`,
    `• **Solde actuel :** ${fmt(balance)}`,
    `• **Solde prévu à 30j :** ${fmt(balance30d)}`,
    `• **Score de santé :** ${healthScore}/100`,
    `• **Points de vigilance :** ${criticalPoints}`,
    `\nVous pouvez me poser des questions comme :`,
    `*"Comment améliorer ma trésorerie ?"*, *"Suis-je éligible au FlashCredit ?"*, *"Quels sont mes risques ?"*`,
    `\nOu explorez directement les pages dédiées dans le menu.`,
  ].join("\n\n");
}

// ─── Message bubble ────────────────────────────────────────────────────────────
const MessageBubble: React.FC<{ message: Message }> = ({ message }) => {
  const isUser = message.role === "user";

  // Render basic markdown-like formatting
  const renderText = (text: string) => {
    const lines = text.split("\n");
    return lines.map((line, i) => {
      // Bold: **text**
      const formatted = line.replace(
        /\*\*(.+?)\*\*/g,
        (_: string, m: string) => `<strong>${m}</strong>`,
      );
      // Italic: *text*
      const formatted2 = formatted.replace(
        /(?<!\*)\*([^*]+)\*(?!\*)/g,
        (_: string, m: string) => `<em>${m}</em>`,
      );
      return (
        <span key={i}>
          {i > 0 && <br />}
          <span dangerouslySetInnerHTML={{ __html: formatted2 }} />
        </span>
      );
    });
  };

  return (
    <div className={`flex gap-3 ${isUser ? "flex-row-reverse" : "flex-row"}`}>
      {/* Avatar */}
      <div
        className={`w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center ${
          isUser
            ? "bg-primary/20 border border-primary/30"
            : "bg-gradient-primary shadow-glow"
        }`}
      >
        {isUser ? (
          <span className="text-primary text-xs font-bold">Moi</span>
        ) : (
          <Brain size={15} className="text-white" />
        )}
      </div>

      {/* Bubble */}
      <div
        className={`max-w-[80%] px-4 py-3 rounded-2xl text-sm leading-relaxed ${
          isUser
            ? "bg-primary/10 border border-primary/20 text-white rounded-tr-sm"
            : "bg-surface border border-white/[0.08] text-text-secondary rounded-tl-sm"
        }`}
      >
        {renderText(message.text)}
        <p className="text-text-muted text-[10px] mt-1.5 text-right">
          {message.timestamp.toLocaleTimeString("fr-FR", {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </p>
      </div>
    </div>
  );
};

// ─── Main component ────────────────────────────────────────────────────────────
const AssistantPage: React.FC = () => {
  const { user } = useAuthStore();
  const { data: dashboard } = useDashboard();
  const { data: forecast } = useForecast(30);

  const [messages, setMessages] = useState<Message[]>([
    {
      id: "welcome",
      role: "assistant",
      text: `Bonjour ${user?.firstName ? user.firstName : ""} ! Je suis votre **Conseiller Financier IA** FlowGuard.\n\nJe peux analyser votre trésorerie en temps réel et vous donner des conseils personnalisés. Que souhaitez-vous savoir ?`,
      timestamp: new Date(),
    },
  ]);
  const [input, setInput] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  // Build context from live data
  const context = {
    balance: dashboard?.currentBalance ?? 0,
    balance30d: dashboard?.predictedBalance30d ?? 0,
    trend: dashboard?.balanceTrend ?? 0,
    healthScore: dashboard?.healthScore ?? 0,
    hasAlert: dashboard?.hasHighAlert ?? false,
    alertMessage: dashboard?.highAlertMessage,
    criticalPoints: forecast?.criticalPoints?.length ?? 0,
    confidenceScore: forecast?.confidenceScore ?? 0,
    firstName: user?.firstName ?? "",
  };

  const quickQuestions: QuickQuestion[] = [
    {
      label: "Situation globale",
      icon: <TrendingUp size={13} />,
      question: "Quelle est ma situation de trésorerie actuelle ?",
      color: "bg-primary/10 border-primary/20 text-primary",
    },
    {
      label: "Améliorer mes flux",
      icon: <Sparkles size={13} />,
      question: "Comment améliorer ma trésorerie ?",
      color: "bg-success/10 border-success/20 text-success",
    },
    {
      label: "Prévisions",
      icon: <Target size={13} />,
      question: "Quelles sont mes prévisions financières ?",
      color: "bg-info/10 border-info/20 text-info",
    },
    {
      label: "Risques",
      icon: <AlertTriangle size={13} />,
      question: "Quels sont mes risques financiers actuels ?",
      color: "bg-warning/10 border-warning/20 text-warning",
    },
    {
      label: "FlashCredit",
      icon: <Zap size={13} />,
      question: "Suis-je éligible au FlashCredit ?",
      color: "bg-warning/10 border-warning/20 text-warning",
    },
    {
      label: "Dépenses",
      icon: <PieChart size={13} />,
      question: "Comment optimiser mes dépenses par catégorie ?",
      color: "bg-purple/10 border-purple/20 text-purple",
    },
  ];

  // Scroll to bottom on new message
  useEffect(() => {
    if (bottomRef.current?.scrollIntoView) {
      bottomRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, isTyping]);

  const sendMessage = useCallback(
    (text: string) => {
      if (!text.trim()) return;

      const userMsg: Message = {
        id: `user-${Date.now()}`,
        role: "user",
        text: text.trim(),
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, userMsg]);
      setInput("");
      setIsTyping(true);

      // Simulate AI "thinking" delay
      setTimeout(
        () => {
          const answer = generateAnswer(text, context);
          const assistantMsg: Message = {
            id: `assistant-${Date.now()}`,
            role: "assistant",
            text: answer,
            timestamp: new Date(),
          };
          setMessages((prev) => [...prev, assistantMsg]);
          setIsTyping(false);
        },
        600 + Math.random() * 400,
      );
    },
    [context],
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    sendMessage(input);
  };

  const handleReset = () => {
    setMessages([
      {
        id: "welcome-reset",
        role: "assistant",
        text: `Conversation réinitialisée. Comment puis-je vous aider ${user?.firstName ?? ""} ?`,
        timestamp: new Date(),
      },
    ]);
  };

  return (
    <Layout title="Conseiller IA">
      <div className="max-w-3xl mx-auto space-y-4 animate-slide-up h-full flex flex-col">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="page-title flex items-center gap-2">
              <Brain size={22} className="text-primary" />
              Conseiller Financier IA
            </h1>
            <p className="page-subtitle">
              Posez n&apos;importe quelle question sur votre trésorerie
            </p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<RefreshCw size={14} />}
            onClick={handleReset}
          >
            Réinitialiser
          </Button>
        </div>

        {/* Quick questions */}
        <div>
          <p className="text-text-muted text-xs mb-2 font-medium uppercase tracking-wider">
            Questions rapides
          </p>
          <div className="flex flex-wrap gap-2">
            {quickQuestions.map((qq) => (
              <button
                key={qq.label}
                onClick={() => sendMessage(qq.question)}
                disabled={isTyping}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-xs font-medium transition-all hover:opacity-80 disabled:opacity-40 ${qq.color}`}
              >
                {qq.icon}
                {qq.label}
              </button>
            ))}
          </div>
        </div>

        {/* Chat window */}
        <Card className="flex-1 flex flex-col overflow-hidden">
          <div className="flex-1 overflow-y-auto p-4 space-y-4 min-h-[360px] max-h-[460px]">
            {messages.map((msg) => (
              <MessageBubble key={msg.id} message={msg} />
            ))}

            {/* Typing indicator */}
            {isTyping && (
              <div className="flex gap-3">
                <div className="w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center bg-gradient-primary shadow-glow">
                  <Brain size={15} className="text-white" />
                </div>
                <div className="px-4 py-3 bg-surface border border-white/[0.08] rounded-2xl rounded-tl-sm">
                  <div className="flex gap-1 items-center h-4">
                    <span className="w-2 h-2 rounded-full bg-primary/60 animate-pulse" />
                    <span
                      className="w-2 h-2 rounded-full bg-primary/60 animate-pulse"
                      style={{ animationDelay: "0.2s" }}
                    />
                    <span
                      className="w-2 h-2 rounded-full bg-primary/60 animate-pulse"
                      style={{ animationDelay: "0.4s" }}
                    />
                  </div>
                </div>
              </div>
            )}

            <div ref={bottomRef} />
          </div>

          {/* Input */}
          <div className="border-t border-white/[0.08] p-3">
            <form onSubmit={handleSubmit} className="flex gap-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="Ex. : Comment améliorer ma trésorerie ?"
                disabled={isTyping}
                className="flex-1 bg-white/[0.04] border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm placeholder:text-text-muted focus:outline-none focus:border-primary/40 focus:ring-2 focus:ring-primary/15 transition disabled:opacity-50"
              />
              <Button
                type="submit"
                variant="gradient"
                size="md"
                leftIcon={<Send size={15} />}
                disabled={!input.trim() || isTyping}
              >
                Envoyer
              </Button>
            </form>
            <p className="text-text-muted text-[11px] mt-2 text-center">
              Les réponses sont basées sur vos données en temps réel et les
              prévisions IA FlowGuard.
            </p>
          </div>
        </Card>
      </div>
    </Layout>
  );
};

export default AssistantPage;
