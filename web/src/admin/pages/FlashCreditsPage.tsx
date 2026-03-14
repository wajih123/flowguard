import React, { useState } from "react";
import { CheckCircle, XCircle, Filter, AlertTriangle } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card } from "@/components/ui/Card";
import { Select } from "@/components/ui/Input";
import { Badge, creditStatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Loader } from "@/components/ui/Loader";
import { EmptyState } from "@/components/ui/EmptyState";
import { Modal } from "@/components/ui/Modal";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { adminApi, type AdminCredit } from "@/api/admin";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const AdminCreditsPage: React.FC = () => {
  const [statusFilter, setStatusFilter] = useState("");
  const [rejectModal, setRejectModal] = useState<{
    id: string;
    reason: string;
  } | null>(null);
  const [writeOffId, setWriteOffId] = useState<string | null>(null);
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "credits", statusFilter],
    queryFn: () => adminApi.listCredits({ status: statusFilter || undefined }),
    staleTime: 60_000,
  });

  const approve = useMutation({
    mutationFn: adminApi.approveCredit,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "credits"] }),
  });

  const reject = useMutation({
    mutationFn: ({ creditId, reason }: { creditId: string; reason: string }) =>
      adminApi.rejectCredit(creditId, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "credits"] });
      setRejectModal(null);
    },
  });

  const writeOff = useMutation({
    mutationFn: (id: string) => adminApi.writeOffCredit(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "credits"] });
      setWriteOffId(null);
    },
  });

  const credits: AdminCredit[] = data?.content ?? [];

  return (
    <AdminLayout title="Flash Crédits">
      <div className="max-w-7xl mx-auto space-y-6 animate-fade-in">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white">Flash Crédits</h1>
            <p className="text-text-secondary text-sm mt-1">
              {data?.totalElements ?? 0} demandes au total
            </p>
          </div>
          <div className="w-48">
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={[
                { value: "", label: "Tous statuts" },
                { value: "PENDING", label: "En attente" },
                { value: "APPROVED", label: "Approuvé" },
                { value: "REPAID", label: "Remboursé" },
                { value: "OVERDUE", label: "En retard" },
                { value: "REJECTED", label: "Refusé" },
              ]}
            />
          </div>
        </div>

        <Card padding="none">
          {isLoading ? (
            <Loader />
          ) : credits.length === 0 ? (
            <EmptyState
              title="Aucun crédit"
              description="Aucune demande ne correspond aux filtres"
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Utilisateur</th>
                    <th>Montant</th>
                    <th>Frais</th>
                    <th>TAEG</th>
                    <th>Objet</th>
                    <th>Statut</th>
                    <th>Échéance</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {credits.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <div>
                          <p className="text-white text-sm font-medium">
                            {c.userFullName}
                          </p>
                          <p className="text-text-muted text-xs font-mono">
                            {c.userEmailMasked}
                          </p>
                        </div>
                      </td>
                      <td className="font-semibold">{fmt(c.amount)}</td>
                      <td className="text-warning">{fmt(c.fee)}</td>
                      <td className="text-text-secondary">
                        {c.taegPercent ? `${c.taegPercent}%` : "—"}
                      </td>
                      <td className="max-w-xs truncate text-text-secondary">
                        {c.purpose}
                      </td>
                      <td>
                        <Badge variant={creditStatusBadge(c.status)}>
                          {c.status}
                        </Badge>
                      </td>
                      <td className="text-text-secondary text-xs">
                        {format(parseISO(c.dueDate), "d MMM yyyy", {
                          locale: fr,
                        })}
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          {c.status === "PENDING" && (
                            <>
                              <Button
                                variant="ghost"
                                size="sm"
                                leftIcon={
                                  <CheckCircle
                                    size={14}
                                    className="text-success"
                                  />
                                }
                                isLoading={approve.isPending}
                                onClick={() => approve.mutate(c.id)}
                              >
                                Approuver
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                leftIcon={
                                  <XCircle size={14} className="text-danger" />
                                }
                                onClick={() =>
                                  setRejectModal({ id: c.id, reason: "" })
                                }
                              >
                                Refuser
                              </Button>
                            </>
                          )}
                          {c.status === "OVERDUE" && (
                            <Button
                              variant="ghost"
                              size="sm"
                              leftIcon={
                                <AlertTriangle
                                  size={14}
                                  className="text-warning"
                                />
                              }
                              onClick={() => setWriteOffId(c.id)}
                            >
                              Passer en perte
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      {/* Reject modal */}
      <Modal
        isOpen={!!rejectModal}
        onClose={() => setRejectModal(null)}
        title="Refuser la demande"
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-text-secondary text-sm">
            Indiquez la raison du refus (optionnel) :
          </p>
          <textarea
            value={rejectModal?.reason ?? ""}
            onChange={(e) =>
              setRejectModal((prev) =>
                prev ? { ...prev, reason: e.target.value } : null,
              )
            }
            placeholder="Motif du refus…"
            rows={3}
            className="w-full rounded-xl border border-white/10 bg-white/[0.04] text-white p-3 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/40"
          />
          <div className="flex gap-3">
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => setRejectModal(null)}
            >
              Annuler
            </Button>
            <Button
              variant="danger"
              className="flex-1"
              isLoading={reject.isPending}
              onClick={() =>
                rejectModal &&
                reject.mutate({
                  creditId: rejectModal.id,
                  reason: rejectModal.reason,
                })
              }
            >
              Confirmer le refus
            </Button>
          </div>
        </div>
      </Modal>

      {/* Write-off confirm */}
      <ConfirmModal
        isOpen={!!writeOffId}
        title="Passer en perte"
        description="Ce crédit sera marqué comme irrécouvrable. Cette action est définitive."
        confirmLabel="Passer en perte"
        danger
        onConfirm={() => {
          if (writeOffId) writeOff.mutate(writeOffId);
        }}
        onClose={() => setWriteOffId(null)}
        isLoading={writeOff.isPending}
      />
    </AdminLayout>
  );
};

export default AdminCreditsPage;
