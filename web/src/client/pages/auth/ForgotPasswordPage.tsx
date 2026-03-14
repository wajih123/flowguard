import React, { useState } from "react";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Mail, ArrowLeft, CheckCircle2 } from "lucide-react";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { AlertBanner } from "@/components/ui/AlertBanner";
import apiClient from "@/api/client";

const schema = z.object({
  email: z.string().email("Adresse email invalide"),
});
type FormData = z.infer<typeof schema>;

const ForgotPasswordPage: React.FC = () => {
  const [sent, setSent] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormData) => {
    setIsLoading(true);
    setError(null);
    try {
      await apiClient.post("/api/auth/forgot-password", { email: data.email });
      setSent(true);
    } catch {
      setError("Une erreur est survenue. Veuillez réessayer.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="flex items-center gap-3 mb-10">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-purple flex items-center justify-center text-white font-bold text-sm">
            FG
          </div>
          <span className="text-white font-bold">FlowGuard</span>
        </div>

        {sent ? (
          <div className="glass-card p-8 text-center">
            <div className="w-14 h-14 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-4">
              <CheckCircle2 size={28} className="text-success" />
            </div>
            <h1 className="text-xl font-bold text-white mb-2">
              Email envoyé !
            </h1>
            <p className="text-text-secondary text-sm mb-6">
              Si un compte FlowGuard est associé à l&apos;adresse{" "}
              <span className="text-white font-medium">
                {getValues("email")}
              </span>
              {", "}vous recevrez un lien de réinitialisation sous quelques
              minutes.
            </p>
            <Link
              to="/login"
              className="inline-flex items-center gap-2 text-primary hover:text-primary-light text-sm font-medium transition-colors"
            >
              <ArrowLeft size={14} />
              Retour à la connexion
            </Link>
          </div>
        ) : (
          <>
            <h1 className="text-2xl font-bold text-white mb-2">
              Mot de passe oublié
            </h1>
            <p className="text-text-secondary mb-8">
              Entrez votre adresse email pour recevoir un lien de
              réinitialisation.
            </p>

            {error && (
              <div className="mb-5">
                <AlertBanner message={error} onClose={() => setError(null)} />
              </div>
            )}

            <div className="glass-card p-6">
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
                <Button
                  type="submit"
                  isLoading={isLoading}
                  className="w-full"
                  size="lg"
                >
                  Envoyer le lien
                </Button>
              </form>
            </div>

            <p className="text-center text-text-secondary text-sm mt-6">
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 text-primary hover:text-primary-light font-medium transition-colors"
              >
                <ArrowLeft size={14} />
                Retour à la connexion
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
