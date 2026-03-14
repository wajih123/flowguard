import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Mail,
  Lock,
  Eye,
  EyeOff,
  User,
  Building2,
  Check,
  Zap,
  Star,
  Shield,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { Input, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { AlertBanner } from "@/components/ui/AlertBanner";
import type { UserType, Plan } from "@/domain/User";

// ─── Step 1: Company info ─────────────────────────────────────────────────────
const step1Schema = z.object({
  companyName: z.string().min(2, "Nom de société requis"),
  userType: z.enum(["TPE", "PME", "SME"]),
  siret: z
    .string()
    .length(14, "SIRET: 14 chiffres")
    .regex(/^\d+$/, "Chiffres uniquement")
    .optional()
    .or(z.literal("")),
});

// ─── Step 2: Personal info ────────────────────────────────────────────────────
const step2Schema = z
  .object({
    firstName: z.string().min(2, "Prénom requis"),
    lastName: z.string().min(2, "Nom requis"),
    email: z.string().email("Email invalide"),
    password: z
      .string()
      .min(8, "Min. 8 caractères")
      .regex(/[A-Z]/, "Au moins une majuscule")
      .regex(/\d/, "Au moins un chiffre"),
    confirmPassword: z.string(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "Les mots de passe ne correspondent pas",
    path: ["confirmPassword"],
  });

type Step1Data = z.infer<typeof step1Schema>;
type Step2Data = z.infer<typeof step2Schema>;

const plans: {
  id: Plan;
  name: string;
  price: string;
  features: string[];
  icon: React.ReactNode;
  highlight?: boolean;
}[] = [
  {
    id: "FREE",
    name: "Starter",
    price: "Gratuit",
    features: ["1 compte bancaire", "Prévisions 30j", "Alertes basiques"],
    icon: <Zap size={20} />,
  },
  {
    id: "PRO",
    name: "Pro",
    price: "49 €/mois",
    features: [
      "5 comptes bancaires",
      "Prévisions 90j",
      "Scénarios illimités",
      "Flash Crédit",
    ],
    icon: <Star size={20} />,
    highlight: true,
  },
  {
    id: "SCALE",
    name: "Scale",
    price: "149 €/mois",
    features: [
      "Comptes illimités",
      "Prévisions 180j",
      "API access",
      "Support dédié",
      "Gestion équipe",
    ],
    icon: <Shield size={20} />,
  },
];

const stepLabels = ["Société", "Compte", "Abonnement"];

const RegisterBusinessPage: React.FC = () => {
  const {
    register: registerUser,
    isLoading,
    error,
    clearError,
  } = useAuthStore();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [showPwd, setShowPwd] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState<Plan>("PRO");
  const [step1Data, setStep1Data] = useState<Step1Data | null>(null);

  const form1 = useForm<Step1Data>({
    resolver: zodResolver(step1Schema),
    defaultValues: { userType: "TPE" },
  });
  const form2 = useForm<Step2Data>({ resolver: zodResolver(step2Schema) });

  const onStep1 = (data: Step1Data) => {
    setStep1Data(data);
    setStep(2);
  };

  const onStep2 = () => {
    setStep(3);
  };

  const onFinish = async () => {
    const d2 = form2.getValues();
    if (!step1Data) return;
    try {
      await registerUser({
        firstName: d2.firstName,
        lastName: d2.lastName,
        email: d2.email,
        password: d2.password,
        companyName: step1Data.companyName,
        userType: step1Data.userType as UserType,
      });
      navigate("/dashboard");
    } catch {
      // error in store
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-lg">
        {/* Logo */}
        <div className="flex items-center gap-3 mb-8">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-purple flex items-center justify-center text-white font-bold text-sm">
            FG
          </div>
          <span className="text-white font-bold">FlowGuard</span>
          <span className="ml-auto text-xs text-text-muted">
            Compte Professionnel
          </span>
        </div>

        {/* Stepper */}
        <div className="flex items-center gap-2 mb-8">
          {stepLabels.map((label, i) => {
            const idx = i + 1;
            const done = step > idx;
            const active = step === idx;
            return (
              <React.Fragment key={label}>
                <div className="flex items-center gap-2">
                  <div
                    className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0 transition-colors ${done ? "bg-success text-white" : ""} ${!done && active ? "bg-primary text-white" : ""} ${!done && !active ? "bg-white/10 text-text-muted" : ""}`}
                  >
                    {done ? <Check size={12} /> : idx}
                  </div>
                  <span
                    className={`text-xs font-medium transition-colors ${active ? "text-white" : ""} ${!active && done ? "text-success" : ""} ${!active && !done ? "text-text-muted" : ""}`}
                  >
                    {label}
                  </span>
                </div>
                {i < stepLabels.length - 1 && (
                  <div
                    className={`flex-1 h-px transition-colors ${step > idx ? "bg-success/40" : "bg-white/10"}`}
                  />
                )}
              </React.Fragment>
            );
          })}
        </div>

        {error && (
          <div className="mb-5">
            <AlertBanner message={error} onClose={clearError} />
          </div>
        )}

        {/* Step 1: Company */}
        {step === 1 && (
          <div className="glass-card p-8">
            <h1 className="text-xl font-bold text-white mb-1">Votre société</h1>
            <p className="text-text-secondary text-sm mb-6">
              Informations sur votre entreprise
            </p>
            <form
              onSubmit={form1.handleSubmit(onStep1)}
              className="space-y-5"
              noValidate
            >
              <Input
                {...form1.register("companyName")}
                label="Nom de la société"
                placeholder="Ma Société SAS"
                leftIcon={<Building2 size={16} />}
                error={form1.formState.errors.companyName?.message}
              />
              <Select
                {...form1.register("userType")}
                label="Type de structure"
                error={form1.formState.errors.userType?.message}
                options={[
                  { value: "TPE", label: "TPE (< 10 salariés)" },
                  { value: "PME", label: "PME (10–250 salariés)" },
                  { value: "SME", label: "Groupe / Holding" },
                ]}
              />
              <Input
                {...form1.register("siret")}
                label="SIRET (optionnel)"
                placeholder="12345678901234"
                error={form1.formState.errors.siret?.message}
              />
              <Button type="submit" className="w-full" size="lg">
                Continuer
              </Button>
            </form>
          </div>
        )}

        {/* Step 2: Account */}
        {step === 2 && (
          <div className="glass-card p-8">
            <h1 className="text-xl font-bold text-white mb-1">Votre compte</h1>
            <p className="text-text-secondary text-sm mb-6">
              Accès à votre espace FlowGuard
            </p>
            <form
              onSubmit={form2.handleSubmit(onStep2)}
              className="space-y-4"
              noValidate
            >
              <div className="grid grid-cols-2 gap-4">
                <Input
                  {...form2.register("firstName")}
                  label="Prénom"
                  placeholder="Jean"
                  leftIcon={<User size={16} />}
                  error={form2.formState.errors.firstName?.message}
                />
                <Input
                  {...form2.register("lastName")}
                  label="Nom"
                  placeholder="Dupont"
                  error={form2.formState.errors.lastName?.message}
                />
              </div>
              <Input
                {...form2.register("email")}
                label="Email professionnelle"
                type="email"
                placeholder="j.dupont@masociete.fr"
                leftIcon={<Mail size={16} />}
                error={form2.formState.errors.email?.message}
              />
              <Input
                {...form2.register("password")}
                label="Mot de passe"
                type={showPwd ? "text" : "password"}
                placeholder="••••••••"
                leftIcon={<Lock size={16} />}
                error={form2.formState.errors.password?.message}
                rightIcon={
                  <button
                    type="button"
                    onClick={() => setShowPwd((v) => !v)}
                    className="text-text-muted hover:text-white"
                    aria-label={showPwd ? "Masquer" : "Afficher"}
                  >
                    {showPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                }
              />
              <Input
                {...form2.register("confirmPassword")}
                label="Confirmer le mot de passe"
                type="password"
                placeholder="••••••••"
                error={form2.formState.errors.confirmPassword?.message}
              />
              <div className="flex gap-3 pt-1">
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={() => setStep(1)}
                >
                  Retour
                </Button>
                <Button type="submit" className="flex-1">
                  Continuer
                </Button>
              </div>
            </form>
          </div>
        )}

        {/* Step 3: Plan */}
        {step === 3 && (
          <div>
            <h1 className="text-xl font-bold text-white mb-1">
              Choisissez votre offre
            </h1>
            <p className="text-text-secondary text-sm mb-6">
              Changez à tout moment depuis votre espace
            </p>
            <div className="space-y-3 mb-6">
              {plans.map((plan) => (
                <button
                  key={plan.id}
                  onClick={() => setSelectedPlan(plan.id)}
                  className={`w-full p-4 rounded-xl border transition-all text-left ${
                    selectedPlan === plan.id
                      ? "border-primary/60 bg-primary/10"
                      : "border-white/[0.06] bg-surface hover:border-white/20"
                  } ${plan.highlight ? "ring-1 ring-primary/30" : ""}`}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`p-2 rounded-lg ${selectedPlan === plan.id ? "bg-primary/20 text-primary" : "bg-white/10 text-text-secondary"}`}
                    >
                      {plan.icon}
                    </div>
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <p className="font-semibold text-white text-sm">
                          {plan.name}
                        </p>
                        {plan.highlight && (
                          <span className="text-xs px-2 py-0.5 rounded-full bg-primary/20 text-primary font-medium">
                            Populaire
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-text-secondary mt-0.5">
                        {plan.features.join(" · ")}
                      </p>
                    </div>
                    <p className="text-sm font-bold text-white shrink-0">
                      {plan.price}
                    </p>
                  </div>
                </button>
              ))}
            </div>
            <div className="flex gap-3">
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => setStep(2)}
              >
                Retour
              </Button>
              <Button
                className="flex-1"
                isLoading={isLoading}
                onClick={onFinish}
              >
                Créer mon compte
              </Button>
            </div>
          </div>
        )}

        <p className="text-center text-text-secondary text-sm mt-6">
          Déjà un compte ?{" "}
          <Link
            to="/login"
            className="text-primary hover:text-primary-light font-medium"
          >
            Se connecter
          </Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterBusinessPage;
