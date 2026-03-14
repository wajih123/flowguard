import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Settings, Lock, Check, X, Pencil } from "lucide-react";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Loader } from "@/components/ui/Loader";
import { Badge } from "@/components/ui/Badge";
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

const valueTypeBadge = (type: string) => {
  const map: Record<string, "primary" | "success" | "warning" | "muted"> = {
    string: "muted",
    number: "primary",
    boolean: "success",
    json: "warning",
  };
  return map[type.toLowerCase()] ?? "muted";
};

const AdminConfigPage: React.FC = () => {
  const qc = useQueryClient();
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  const { data: configs, isLoading } = useQuery({
    queryKey: ["admin", "config"],
    queryFn: adminApi.listConfig,
    staleTime: 30_000,
  });

  const save = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      adminApi.updateConfig(key, value),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "config"] });
      setEditingKey(null);
    },
  });

  const startEdit = (configKey: string, currentValue: string) => {
    setEditingKey(configKey);
    setEditValue(currentValue);
  };

  const cancelEdit = () => {
    setEditingKey(null);
    setEditValue("");
  };

  return (
    <AdminLayout title="Configuration système">
      <SuperAdminGate>
        <div className="max-w-3xl mx-auto space-y-6 animate-fade-in">
          <div>
            <h1 className="text-2xl font-bold text-white">
              Configuration système
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Paramètres globaux de la plateforme. Les modifications sont
              appliquées immédiatement.
            </p>
          </div>

          <Card padding="none">
            {isLoading ? (
              <div className="p-8">
                <Loader />
              </div>
            ) : (
              <div className="divide-y divide-white/[0.04]">
                {(configs ?? []).map((cfg) => (
                  <div
                    key={cfg.id}
                    className="flex items-start gap-4 px-5 py-4"
                  >
                    <Settings
                      size={16}
                      className="text-text-muted mt-1 shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="text-white text-sm font-medium font-mono">
                          {cfg.configKey}
                        </p>
                        <Badge variant={valueTypeBadge(cfg.valueType)}>
                          {cfg.valueType}
                        </Badge>
                      </div>
                      {cfg.description && (
                        <p className="text-text-muted text-xs mt-0.5">
                          {cfg.description}
                        </p>
                      )}
                      <p className="text-text-muted text-xs mt-0.5">
                        Modifié le{" "}
                        {format(parseISO(cfg.updatedAt), "d MMM yyyy à HH:mm", {
                          locale: fr,
                        })}
                      </p>

                      {editingKey === cfg.configKey ? (
                        <div className="flex items-center gap-2 mt-3">
                          <Input
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            className="h-9 text-sm font-mono"
                            autoFocus
                            onKeyDown={(e) => {
                              if (e.key === "Enter")
                                save.mutate({
                                  key: cfg.configKey,
                                  value: editValue,
                                });
                              if (e.key === "Escape") cancelEdit();
                            }}
                          />
                          <Button
                            variant="primary"
                            size="sm"
                            isLoading={save.isPending}
                            onClick={() =>
                              save.mutate({
                                key: cfg.configKey,
                                value: editValue,
                              })
                            }
                            leftIcon={<Check size={14} />}
                          >
                            Sauvegarder
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={cancelEdit}
                            leftIcon={<X size={14} />}
                          >
                            Annuler
                          </Button>
                        </div>
                      ) : (
                        <p className="text-text-secondary text-sm font-mono mt-1 break-all">
                          {cfg.configValue}
                        </p>
                      )}
                    </div>

                    {editingKey !== cfg.configKey && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() =>
                          startEdit(cfg.configKey, cfg.configValue)
                        }
                        leftIcon={<Pencil size={14} />}
                      >
                        Modifier
                      </Button>
                    )}
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

export default AdminConfigPage;
