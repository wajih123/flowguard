import React, { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Mail,
  Lock,
  User,
  Building2,
  Eye,
  EyeOff,
  ShieldCheck,
  ArrowLeft,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { Input, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { AlertBanner } from "@/components/ui/AlertBanner";
import type { UserType } from "@/domain/User";

const schema = z.object({
  firstName: z.string().min(2, "Prénom requis (min. 2 caractères)"),
  lastName: z.string().min(2, "Nom requis (min. 2 caractères)"),
  email: z.string().email("Adresse email invalide"),
  password: z
    .string()
    .min(8, "Mot de passe de 8 caractères minimum")
    .regex(/[A-Z]/, "Au moins une majuscule")
    .regex(/[0-9]/, "Au moins un chiffre"),
  companyName: z.string().optional(),
  userType: z.enum(["INDIVIDUAL", "FREELANCE", "TPE", "PME", "SME"]),
});
type FormData = z.infer<typeof schema>;

// ── Email verification step (one-time, shown right after registration) ──────────
const VerifyEmailStep: React.FC<{ maskedEmail: string | null }> = ({
  maskedEmail,
}) => {
  const { verifyEmail, cancelEmailVerification, isLoading, error, clearError } =
    useAuthStore();
  const navigate = useNavigate();
  const [digits, setDigits] = useState(["", "", "", "", "", ""]);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    inputRefs.current[0]?.focus();
  }, []);

  const handleChange = (idx: number, val: string) => {
    if (!/^\d?$/.test(val)) return;
    const next = [...digits];
    next[idx] = val;
    setDigits(next);
    if (val && idx < 5) inputRefs.current[idx + 1]?.focus();
    if (next.every((d) => d !== "")) handleVerify(next.join(""));
  };

  const handleKeyDown = (
    idx: number,
    e: React.KeyboardEvent<HTMLInputElement>,
  ) => {
    if (e.key === "Backspace" && !digits[idx] && idx > 0) {
      inputRefs.current[idx - 1]?.focus();
    }
  };

  const handleVerify = async (code: string) => {
    try {
      await verifyEmail(code);
      navigate("/dashboard");
    } catch {
      setDigits(["", "", "", "", "", ""]);
      setTimeout(() => inputRefs.current[0]?.focus(), 50);
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <button
          onClick={cancelEmailVerification}
          className="flex items-center gap-2 text-text-secondary hover:text-white text-sm mb-8 transition-colors"
        >
          <ArrowLeft size={16} /> Retour
        </button>

        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center">
            <ShieldCheck size={24} className="text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white">
              Vérifiez votre email
            </h1>
            <p className="text-text-secondary text-sm">
              Code envoyé à{" "}
              <span className="text-white font-medium">{maskedEmail}</span>
            </p>
          </div>
        </div>

        <p className="text-text-secondary text-sm mb-8">
          Saisissez le code à 6 chiffres reçu par e-mail pour activer votre
          compte. Il expire dans{" "}
          <span className="text-white font-medium">15 minutes</span>.
        </p>

        {error && (
          <div className="mb-5">
            <AlertBanner message={error} onClose={clearError} />
          </div>
        )}

        <div className="flex gap-3 justify-center mb-8">
          {digits.map((d, i) => (
            <input
              key={i}
              ref={(el) => {
                inputRefs.current[i] = el;
              }}
              type="text"
              inputMode="numeric"
              maxLength={1}
              value={d}
              onChange={(e) => handleChange(i, e.target.value)}
              onKeyDown={(e) => handleKeyDown(i, e)}
              className="w-12 h-14 text-center text-2xl font-bold bg-surface border border-white/10 rounded-xl text-white focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-colors"
            />
          ))}
        </div>

        <Button
          onClick={() => handleVerify(digits.join(""))}
          isLoading={isLoading}
          disabled={digits.some((d) => !d) || isLoading}
          className="w-full"
          size="lg"
        >
          Activer mon compte
        </Button>
      </div>
    </div>
  );
};

const RegisterPage: React.FC = () => {
  const {
    register: registerUser,
    isLoading,
    error,
    clearError,
    emailVerificationPending,
    maskedEmail,
  } = useAuthStore();
  const navigate = useNavigate();
  const [showPwd, setShowPwd] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { userType: "TPE" },
  });

  // Redirect to dashboard if verified account (e.g. page refresh after success)
  useEffect(() => {
    if (useAuthStore.getState().isAuthenticated) {
      navigate("/dashboard", { replace: true });
    }
  }, [navigate]);

  if (emailVerificationPending) {
    return <VerifyEmailStep maskedEmail={maskedEmail} />;
  }

  const onSubmit = async (data: FormData) => {
    try {
      await registerUser({ ...data, userType: data.userType as UserType });
      // If emailVerificationPending is now true the VerifyEmailStep renders above.
      // If somehow verification was skipped (future), navigate directly.
      if (useAuthStore.getState().isAuthenticated) {
        navigate("/dashboard");
      }
    } catch {
      // error is set in store
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-lg">
        {/* Logo */}
        <div className="flex items-center gap-3 mb-10">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-purple flex items-center justify-center text-white font-bold text-sm">
            FG
          </div>
          <span className="text-white font-bold">FlowGuard</span>
        </div>

        <h1 className="text-2xl font-bold text-white mb-2">
          Créer votre compte
        </h1>
        <p className="text-text-secondary mb-8">
          Commencez à gérer votre trésorerie avec l'IA
        </p>

        {error && (
          <div className="mb-5">
            <AlertBanner message={error} onClose={clearError} />
          </div>
        )}

        <div className="glass-card p-8">
          <form
            onSubmit={handleSubmit(onSubmit)}
            className="space-y-5"
            noValidate
          >
            <div className="grid grid-cols-2 gap-4">
              <Input
                {...register("firstName")}
                label="Prénom"
                placeholder="Jean"
                error={errors.firstName?.message}
                leftIcon={<User size={16} />}
              />
              <Input
                {...register("lastName")}
                label="Nom"
                placeholder="Dupont"
                error={errors.lastName?.message}
              />
            </div>

            <Input
              {...register("email")}
              label="Email professionnel"
              type="email"
              placeholder="jean@entreprise.fr"
              autoComplete="email"
              error={errors.email?.message}
              leftIcon={<Mail size={16} />}
            />

            <Input
              {...register("password")}
              label="Mot de passe"
              type={showPwd ? "text" : "password"}
              placeholder="••••••••"
              autoComplete="new-password"
              error={errors.password?.message}
              leftIcon={<Lock size={16} />}
              rightIcon={
                <button
                  type="button"
                  onClick={() => setShowPwd((v) => !v)}
                  className="text-text-muted hover:text-white"
                >
                  {showPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              }
            />

            <Input
              {...register("companyName")}
              label="Nom de l'entreprise (optionnel)"
              placeholder="Ma Société SAS"
              error={errors.companyName?.message}
              leftIcon={<Building2 size={16} />}
            />

            <Select
              {...register("userType")}
              label="Type de profil"
              error={errors.userType?.message}
              options={[
                { value: "INDIVIDUAL", label: "Particulier" },
                { value: "FREELANCE", label: "Freelance / Auto-entrepreneur" },
                { value: "TPE", label: "TPE (< 10 employés)" },
                { value: "PME", label: "PME (10–250 employés)" },
                { value: "SME", label: "Grande entreprise" },
              ]}
            />

            <Button
              type="submit"
              isLoading={isLoading}
              className="w-full"
              size="lg"
            >
              Créer mon compte
            </Button>
          </form>
        </div>

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

export default RegisterPage;
