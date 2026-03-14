import React from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Flag, Lock } from "lucide-react";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card } from "@/components/ui/Card";
import { Toggle } from "@/components/ui/Toggle";
import { Loader } from "@/components/ui/Loader";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/authStore";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const SuperAdminGate: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const isSuperAdmin = useAuthStore((s) => s.user?.role === "ROLE_SUPER_ADMIN");
  if (!isSuperAdmin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center animate-fade-in">
        <Lock size={40} className="text-text-muted" />
        <h2 className="text-xl font-bold text-white">Accès réservé</h2>
        <p className="text-text-secondary max-w-sm">
          Cette section est réservée au Super Admin. Contactez le fondateur pour
          obtenir un accès élevé.
        </p>
      </div>
    );
  }
  return <>{children}</>;
};

const AdminFlagsPage: React.FC = () => {
  const qc = useQueryClient();

  const { data: flags, isLoading } = useQuery({
    queryKey: ["admin", "flags"],
    queryFn: adminApi.listFlags,
    staleTime: 30_000,
  });

  const toggle = useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      adminApi.updateFlag(key, enabled),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "flags"] }),
  });

  return (
    <AdminLayout title="Feature Flags">
      <SuperAdminGate>
        <div className="max-w-3xl mx-auto space-y-6 animate-fade-in">
          <div>
            <h1 className="text-2xl font-bold text-white">Feature Flags</h1>
            <p className="text-text-secondary text-sm mt-1">
              Activez ou désactivez les fonctionnalités en temps réel.
            </p>
          </div>

          <Card padding="none">
            {isLoading ? (
              <Loader />
            ) : (
              <div className="divide-y divide-white/[0.04]">
                {(flags ?? []).map((flag) => (
                  <div
                    key={flag.id}
                    className="flex items-center justify-between px-5 py-4"
                  >
                    <div className="flex items-center gap-3">
                      <Flag
                        size={16}
                        className={
                          flag.enabled ? "text-success" : "text-text-muted"
                        }
                      />
                      <div>
                        <p className="text-white text-sm font-medium font-mono">
                          {flag.flagKey}
                        </p>
                        {flag.description && (
                          <p className="text-text-muted text-xs mt-0.5">
                            {flag.description}
                          </p>
                        )}
                        <p className="text-text-muted text-xs">
                          Modifié le{" "}
                          {format(
                            parseISO(flag.updatedAt),
                            "d MMM yyyy à HH:mm",
                            {
                              locale: fr,
                            },
                          )}
                        </p>
                      </div>
                    </div>
                    <Toggle
                      checked={flag.enabled}
                      onChange={(v) =>
                        toggle.mutate({ key: flag.flagKey, enabled: v })
                      }
                      disabled={toggle.isPending}
                    />
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </SuperAdminGate>
    </AdminLayout>
  );
};

export default AdminFlagsPage;
