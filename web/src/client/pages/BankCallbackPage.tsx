import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { CheckCircle, XCircle, Loader2 } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { useHandleCallback } from "@/hooks/useBanking";
import { bankingApi, type BankAccount } from "@/api/banking";

const POLL_INTERVAL_MS = 3_000;
const POLL_TIMEOUT_MS = 180_000; // 3 min — Bridge sandbox can be slow

/**
 * Page de callback Bridge API.
 * Bridge redirige vers /banking/callback?state=xxx après authentification bancaire.
 * Cette page appelle le backend (qui répond 202 immédiatement), puis poll
 * GET /api/banking/accounts jusqu'à ce que la synchronisation soit terminée.
 */
const BankCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const handleCallback = useHandleCallback();
  const called = useRef(false);

  const [polling, setPolling] = useState(false);
  const [syncedAccounts, setSyncedAccounts] = useState<BankAccount[]>([]);
  const [pollTimedOut, setPollTimedOut] = useState(false);

  // Bridge redirects with ?context=… (our state) and ?success=true|false
  const state = searchParams.get("context") ?? searchParams.get("state");
  // success=false means user exited Bridge Connect without connecting a bank
  const abandoned = searchParams.get("success") === "false";

  // Step 1: trigger the backend callback once
  useEffect(() => {
    if (called.current) return;
    if (abandoned || !state) return;
    called.current = true;

    // CSRF check
    const savedState = sessionStorage.getItem("bridge_context");
    if (savedState && savedState !== state) {
      console.error("Bridge callback: state mismatch (possible CSRF)");
      return;
    }

    handleCallback.mutate(state);
  }, [state, abandoned]);

  // Step 2: once 202 is received, poll GET /api/banking/accounts
  useEffect(() => {
    if (!handleCallback.isSuccess) return;

    setPolling(true);
    const startedAt = Date.now();

    const interval = setInterval(async () => {
      try {
        const accounts = await bankingApi.getAccounts();
        const syncDone = accounts.some(
          (a) => a.syncStatus === "OK" || a.syncStatus === "ERROR",
        );
        if (syncDone) {
          setSyncedAccounts(accounts);
          setPolling(false);
          clearInterval(interval);
          sessionStorage.removeItem("bridge_context");
        } else if (Date.now() - startedAt > POLL_TIMEOUT_MS) {
          setPollTimedOut(true);
          setPolling(false);
          clearInterval(interval);
        }
      } catch {
        // network hiccup — keep polling until timeout
      }
    }, POLL_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [handleCallback.isSuccess]);

  const okAccounts = syncedAccounts.filter((a) => a.syncStatus === "OK");
  const isSuccess = !polling && !pollTimedOut && okAccounts.length > 0;
  const isError =
    handleCallback.isError ||
    pollTimedOut ||
    (!polling && syncedAccounts.length > 0 && okAccounts.length === 0);

  return (
    <Layout>
      <div className="max-w-md mx-auto flex items-center justify-center min-h-[60vh]">
        <Card className="text-center p-8 w-full">
          {!abandoned && (handleCallback.isPending || polling) && (
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

          {isSuccess && (
            <>
              <div className="w-16 h-16 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-4">
                <CheckCircle size={28} className="text-success" />
              </div>
              <h2 className="text-white font-bold text-xl mb-2">
                Banque connectée !
              </h2>
              <p className="text-text-secondary text-sm mb-2">
                {okAccounts.length} compte
                {okAccounts.length > 1 ? "s" : ""} synchronisé
                {okAccounts.length > 1 ? "s" : ""} avec succès.
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

          {(abandoned || isError || (!state && !handleCallback.isPending)) &&
            !isSuccess &&
            !polling && (
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
                    : state
                      ? "La synchronisation a échoué. Réessayez depuis la page Connexion bancaire."
                      : "Paramètre de callback manquant."}
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
