import React from "react";
import {
  Users,
  CreditCard,
  Bell,
  TrendingUp,
  AlertTriangle,
  CheckCircle,
  Clock,
  XCircle,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Loader } from "@/components/ui/Loader";
import { adminApi } from "@/api/admin";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

const AdminDashboardPage: React.FC = () => {
  const { data: stats, isLoading } = useQuery({
    queryKey: ["admin", "stats"],
    queryFn: adminApi.getStats,
    staleTime: 5 * 60 * 1000,
  });

  const statCards = stats
    ? [
        {
          label: "Utilisateurs totaux",
          value: stats.totalUsers,
          icon: <Users size={20} />,
          color: "text-primary",
          to: "/admin/users",
        },
        {
          label: "KYC en attente",
          value: stats.pendingKyc,
          icon: <Clock size={20} />,
          color: "text-warning",
          to: "/admin/users",
        },
        {
          label: "Crédits en attente",
          value: stats.pendingCredits,
          icon: <CreditCard size={20} />,
          color: "text-purple",
          to: "/admin/credits",
        },
        {
          label: "Crédits en retard",
          value: stats.overdueCredits,
          icon: <AlertTriangle size={20} />,
          color: "text-danger",
          to: "/admin/credits",
        },
        {
          label: "Alertes critiques",
          value: stats.criticalAlerts,
          icon: <Bell size={20} />,
          color: "text-danger",
          to: "/admin/alerts",
        },
        {
          label: "Volume crédits (€)",
          value: fmt(stats.totalCreditsAmount),
          icon: <TrendingUp size={20} />,
          color: "text-success",
          to: "/admin/credits",
        },
      ]
    : [];

  return (
    <AdminLayout title="Vue d'ensemble" subtitle="Backoffice FlowGuard">
      <div className="max-w-6xl mx-auto space-y-8 animate-fade-in">
        <div>
          <h1 className="text-2xl font-bold text-white mb-1">
            Tableau de bord admin
          </h1>
          <p className="text-text-secondary text-sm">
            Supervisions en temps réel de la plateforme
          </p>
        </div>

        {isLoading ? (
          <Loader text="Chargement des statistiques…" />
        ) : (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
              {statCards.map((s) => (
                <Link key={s.label} to={s.to}>
                  <Card hover>
                    <div className={`mb-3 ${s.color}`}>{s.icon}</div>
                    <p className="text-2xl font-bold text-white">{s.value}</p>
                    <p className="text-text-secondary text-sm mt-1">
                      {s.label}
                    </p>
                  </Card>
                </Link>
              ))}
            </div>

            {/* Quick actions */}
            <div className="grid lg:grid-cols-2 gap-6">
              <Card>
                <CardHeader title="Actions rapides" />
                <div className="space-y-2">
                  {[
                    {
                      label: "Utilisateurs à vérifier KYC",
                      to: "/admin/users",
                      icon: <Clock size={16} className="text-warning" />,
                      badge: stats?.pendingKyc,
                    },
                    {
                      label: "Crédits à approuver",
                      to: "/admin/credits",
                      icon: <CreditCard size={16} className="text-purple" />,
                      badge: stats?.pendingCredits,
                    },
                    {
                      label: "Alertes critiques",
                      to: "/admin/alerts",
                      icon: <AlertTriangle size={16} className="text-danger" />,
                      badge: stats?.criticalAlerts,
                    },
                  ].map((item) => (
                    <Link
                      key={item.to}
                      to={item.to}
                      className="flex items-center gap-3 p-3 rounded-xl hover:bg-white/[0.04] transition-colors"
                    >
                      {item.icon}
                      <span className="flex-1 text-sm text-text-secondary hover:text-white transition-colors">
                        {item.label}
                      </span>
                      {item.badge ? (
                        <Badge variant={item.badge > 0 ? "warning" : "muted"}>
                          {item.badge}
                        </Badge>
                      ) : null}
                    </Link>
                  ))}
                </div>
              </Card>

              <Card>
                <CardHeader title="État de la plateforme" />
                <div className="space-y-3">
                  {[
                    {
                      label: "API Backend",
                      status: "OK",
                      color: "text-success",
                      icon: <CheckCircle size={16} />,
                    },
                    {
                      label: "ML Service",
                      status: "OK",
                      color: "text-success",
                      icon: <CheckCircle size={16} />,
                    },
                    {
                      label: "Base de données",
                      status: "OK",
                      color: "text-success",
                      icon: <CheckCircle size={16} />,
                    },
                    {
                      label: "Redis Cache",
                      status: "OK",
                      color: "text-success",
                      icon: <CheckCircle size={16} />,
                    },
                    {
                      label: "Nordigen API",
                      status: "OK",
                      color: "text-success",
                      icon: <CheckCircle size={16} />,
                    },
                  ].map((service) => (
                    <div
                      key={service.label}
                      className="flex items-center justify-between py-2 border-b border-white/[0.04] last:border-0"
                    >
                      <span className="text-text-secondary text-sm">
                        {service.label}
                      </span>
                      <div
                        className={`flex items-center gap-1.5 text-sm font-medium ${service.color}`}
                      >
                        {service.icon}
                        {service.status}
                      </div>
                    </div>
                  ))}
                </div>
              </Card>
            </div>
          </>
        )}
      </div>
    </AdminLayout>
  );
};

export default AdminDashboardPage;
