import React, { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Mail,
  Lock,
  Eye,
  EyeOff,
  TrendingUp,
  ArrowLeft,
  ShieldCheck,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { AlertBanner } from "@/components/ui/AlertBanner";

const schema = z.object({
  email: z.string().email("Adresse email invalide"),
  password: z.string().min(1, "Mot de passe requis"),
});
type FormData = z.infer<typeof schema>;

// ── OTP step component ───────────────────────────────────────────────────────
const OtpStep: React.FC<{ maskedEmail: string | null }> = ({ maskedEmail }) => {
  const { verifyOtp, cancelMfa, isLoading, error, clearError } = useAuthStore();
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
    if (val && idx < 5) {
      inputRefs.current[idx + 1]?.focus();
    }
    if (next.every((d) => d !== "")) {
      handleVerify(next.join(""));
    }
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
      await verifyOtp(code);
      navigate("/dashboard");
    } catch {
      setDigits(["", "", "", "", "", ""]);
      setTimeout(() => inputRefs.current[0]?.focus(), 50);
    }
  };

  return (
    <div className="w-full max-w-md">
      <button
        onClick={cancelMfa}
        className="flex items-center gap-2 text-text-secondary hover:text-white text-sm mb-8 transition-colors"
      >
        <ArrowLeft size={16} /> Retour
      </button>

      <div className="flex items-center gap-3 mb-4">
        <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center">
          <ShieldCheck size={24} className="text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-white">Vérification</h1>
          <p className="text-text-secondary text-sm">
            Code envoyé à{" "}
            <span className="text-white font-medium">{maskedEmail}</span>
          </p>
        </div>
      </div>

      <p className="text-text-secondary text-sm mb-8">
        Saisissez le code à 6 chiffres reçu par e-mail. Il expire dans{" "}
        <span className="text-white font-medium">10 minutes</span>.
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
        Confirmer
      </Button>
    </div>
  );
};

// ── Main page ───────────────────────────────────────────────────────────────
const LoginPage: React.FC = () => {
  const { login, mfaPending, maskedEmail, isLoading, error, clearError } =
    useAuthStore();
  const navigate = useNavigate();
  const [showPwd, setShowPwd] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormData) => {
    try {
      await login(data);
      // If MFA is required the store sets mfaPending = true; navigation stays here.
      // If MFA was somehow skipped (future: admin accounts w/ MFA disabled) navigate now.
      if (!useAuthStore.getState().mfaPending) {
        navigate("/dashboard");
      }
    } catch {
      // error is set in store
    }
  };

  return (
    <div className="min-h-screen bg-background flex">
      {/* Left panel */}
      <div className="hidden lg:flex flex-col justify-between w-1/2 bg-surface px-16 py-12 border-r border-white/[0.06]">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-purple flex items-center justify-center text-white font-bold">
            FG
          </div>
          <span className="text-white font-bold text-lg">FlowGuard</span>
        </div>
        <div>
          <div className="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center mb-8">
            <TrendingUp size={32} className="text-primary" />
          </div>
          <h2 className="text-4xl font-bold text-white leading-tight mb-4">
            Gérez votre trésorerie
            <br />
            <span className="gradient-text">avec l'IA</span>
          </h2>
          <p className="text-text-secondary text-lg leading-relaxed">
            Prévisions intelligentes, alertes en temps réel et Flash Crédit
            instantané pour les PME et TPE françaises.
          </p>
          <div className="mt-10 grid grid-cols-3 gap-6">
            {[
              { label: "Précision IA", value: "94%" },
              { label: "Clients actifs", value: "2 400+" },
              { label: "Horizon max", value: "90 jours" },
            ].map((s) => (
              <div key={s.label}>
                <p className="text-2xl font-bold text-primary">{s.value}</p>
                <p className="text-text-muted text-sm">{s.label}</p>
              </div>
            ))}
          </div>
        </div>
        <p className="text-text-muted text-sm">
          © 2026 FlowGuard SAS — Tous droits réservés
        </p>
      </div>

      {/* Right panel */}
      <div className="flex-1 flex items-center justify-center px-6 py-12">
        {mfaPending ? (
          <OtpStep maskedEmail={maskedEmail} />
        ) : (
          <div className="w-full max-w-md">
            {/* Logo mobile */}
            <div className="flex items-center gap-3 mb-10 lg:hidden">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-purple flex items-center justify-center text-white font-bold text-sm">
                FG
              </div>
              <span className="text-white font-bold">FlowGuard</span>
            </div>

            <h1 className="text-2xl font-bold text-white mb-2">
              Bon retour 👋
            </h1>
            <p className="text-text-secondary mb-8">
              Connectez-vous à votre espace trésorerie
            </p>

            {error && (
              <div className="mb-5">
                <AlertBanner message={error} onClose={clearError} />
              </div>
            )}

            <form
              onSubmit={handleSubmit(onSubmit)}
              className="space-y-5"
              noValidate
            >
              <Input
                {...register("email")}
                label="Adresse email"
                type="email"
                placeholder="vous@exemple.fr"
                autoComplete="email"
                error={errors.email?.message}
                leftIcon={<Mail size={16} />}
              />
              <Input
                {...register("password")}
                label="Mot de passe"
                type={showPwd ? "text" : "password"}
                placeholder="••••••••"
                autoComplete="current-password"
                error={errors.password?.message}
                leftIcon={<Lock size={16} />}
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
              <Button
                type="submit"
                isLoading={isLoading}
                className="w-full"
                size="lg"
              >
                Se connecter
              </Button>
            </form>

            <p className="text-center text-text-secondary text-sm mt-6">
              Pas encore de compte ?{" "}
              <Link
                to="/register"
                className="text-primary hover:text-primary-light font-medium"
              >
                Créer un compte
              </Link>
            </p>
            <p className="text-center text-text-secondary text-sm mt-2">
              Compte entreprise ?{" "}
              <Link
                to="/register-business"
                className="text-primary hover:text-primary-light font-medium"
              >
                Inscription Pro
              </Link>
            </p>
            <p className="text-center mt-2">
              <Link
                to="/forgot-password"
                className="text-text-secondary hover:text-white text-sm transition-colors"
              >
                Mot de passe oublié ?
              </Link>
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default LoginPage;
