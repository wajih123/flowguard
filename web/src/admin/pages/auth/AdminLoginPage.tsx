import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Mail, Lock, ShieldCheck, Eye, EyeOff } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { AlertBanner } from "@/components/ui/AlertBanner";

const schema = z.object({
  email: z.string().email("Adresse email invalide"),
  password: z.string().min(1, "Mot de passe requis"),
});
type FormData = z.infer<typeof schema>;

const AdminLoginPage: React.FC = () => {
  const { login, isLoading, error, clearError, isAdmin } = useAuthStore();
  const navigate = useNavigate();
  const [showPwd, setShowPwd] = useState(false);
  const [notAdmin, setNotAdmin] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormData) => {
    setNotAdmin(false);
    try {
      await login(data);
      // After login, check admin status
      if (!useAuthStore.getState().isAdmin) {
        useAuthStore.getState().logout();
        setNotAdmin(true);
        return;
      }
      navigate("/admin");
    } catch {
      // error set in store
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <div className="flex items-center gap-3 mb-10">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-purple to-primary flex items-center justify-center">
            <ShieldCheck size={20} className="text-white" />
          </div>
          <div>
            <p className="font-bold text-white">FlowGuard</p>
            <p className="text-text-muted text-xs">Administration</p>
          </div>
        </div>

        <h1 className="text-2xl font-bold text-white mb-2">Accès backoffice</h1>
        <p className="text-text-secondary mb-8">
          Réservé aux administrateurs FlowGuard
        </p>

        {(error || notAdmin) && (
          <div className="mb-5">
            <AlertBanner
              message={
                notAdmin ? "Accès refusé : compte non autorisé." : error!
              }
              onClose={() => {
                clearError();
                setNotAdmin(false);
              }}
            />
          </div>
        )}

        <div className="glass-card p-8">
          <form
            onSubmit={handleSubmit(onSubmit)}
            className="space-y-5"
            noValidate
          >
            <Input
              {...register("email")}
              label="Email administrateur"
              type="email"
              placeholder="admin@flowguard.fr"
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
              Connexion admin
            </Button>
          </form>
        </div>

        <p className="text-center mt-6">
          <Link
            to="/login"
            className="text-text-muted text-sm hover:text-text-secondary"
          >
            ← Espace client
          </Link>
        </p>
      </div>
    </div>
  );
};

export default AdminLoginPage;
