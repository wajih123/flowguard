import React, { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { CheckCircle, XCircle, Loader2 } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { useHandleCallback } from "@/hooks/useBanking";

/**
 * Page de callback Bridge API.
 * Bridge redirige vers /banking/callback?state=xxx après authentification bancaire.
 * Cette page appelle le backend pour finaliser la synchronisation.
 */
const BankCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const handleCallback = useHandleCallback();
  const called = useRef(false);

  // Bridge redirects with ?context=… (our state) and ?success=true|false
  const state = searchParams.get("context") ?? searchParams.get("state");
  // success=false means user exited Bridge Connect without connecting a bank
  const abandoned = searchParams.get("success") === "false";

  useEffect(() => {
    if (called.current) return;
    // User abandoned the flow — no bank was connected, don't call the backend
    if (abandoned || !state) return;
    called.current = true;

    // CSRF check: state must match what we stored when starting the connect
    const savedState = sessionStorage.getItem("bridge_context");
    if (savedState && savedState !== state) {
      console.error("Bridge callback: state mismatch (possible CSRF)");
      return;
    }

    handleCallback.mutate(state);
  }, [state, abandoned]);

  return (
    <Layout>
      <div className="max-w-md mx-auto flex items-center justify-center min-h-[60vh]">
        <Card className="text-center p-8 w-full">
          {!abandoned && handleCallback.isPending && (
            <>
              <div className="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-4">
                <Loader2 size={28} className="text-primary animate-spin" />
              </div>
              <h2 className="text-white font-bold text-xl mb-2">
                Synchronisation en cours…
              </h2>
              <p className="text-text-secondary text-sm">
                Nous importons vos comptes et transactions. Ceci peut prendre
                quelques secondes.
              </p>
            </>
          )}

          {!abandoned && handleCallback.isSuccess && (
            <>
              <div className="w-16 h-16 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-4">
                <CheckCircle size={28} className="text-success" />
              </div>
              <h2 className="text-white font-bold text-xl mb-2">
                Banque connectée !
              </h2>
              <p className="text-text-secondary text-sm mb-2">
                {handleCallback.data.accounts_synced} compte
                {handleCallback.data.accounts_synced > 1 ? "s" : ""} synchronisé
                {handleCallback.data.accounts_synced > 1 ? "s" : ""} avec
                succès.
              </p>
              <p className="text-text-muted text-xs mb-6">
                Les prévisions IA vont se recalculer dans les prochaines
                minutes.
              </p>
              <Button
                variant="gradient"
                onClick={() => navigate("/dashboard")}
                className="w-full"
              >
                Voir mon tableau de bord
              </Button>
            </>
          )}

          {(abandoned ||
            handleCallback.isError ||
            (!state && !handleCallback.isPending)) &&
            !handleCallback.isSuccess && (
              <>
                <div className="w-16 h-16 rounded-full bg-warning/10 flex items-center justify-center mx-auto mb-4">
                  <XCircle
                    size={28}
                    className={abandoned ? "text-warning" : "text-danger"}
                  />
                </div>
                <h2 className="text-white font-bold text-xl mb-2">
                  {abandoned ? "Connexion annulée" : "Connexion échouée"}
                </h2>
                <p className="text-text-secondary text-sm mb-6">
                  {abandoned
                    ? "Vous avez quitté le processus de connexion. Vous pouvez réessayer à tout moment."
                    : !state
                      ? "Paramètre de callback manquant."
                      : "La synchronisation a échoué. Réessayez depuis la page Connexion bancaire."}
                </p>
                <Button
                  variant={abandoned ? "gradient" : "outline"}
                  onClick={() => navigate("/bank-connect")}
                  className="w-full"
                >
                  {abandoned ? "Connecter ma banque" : "Réessayer"}
                </Button>
              </>
            )}
        </Card>
      </div>
    </Layout>
  );
};

export default BankCallbackPage;
