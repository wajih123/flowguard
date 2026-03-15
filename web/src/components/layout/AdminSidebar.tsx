import React from "react";
import { NavLink, useNavigate, Link } from "react-router-dom";
import {
  LayoutDashboard,
  Users,
  CreditCard,
  Bell,
  BarChart3,
  Brain,
  LogOut,
  ShieldCheck,
  Flag,
  Settings2,
  UserCog,
  ClipboardList,
  ChevronLeft,
  Lock,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";

const ROLE_BADGE: Record<string, string> = {
  ROLE_SUPER_ADMIN: "Super Admin",
  ROLE_ADMIN: "Admin",
};

const ROLE_COLOR: Record<string, string> = {
  ROLE_SUPER_ADMIN: "bg-primary/20 text-primary",
  ROLE_ADMIN: "bg-purple/20 text-purple",
};

const adminNav = [
  {
    label: "Vue d'ensemble",
    to: "/admin/dashboard",
    icon: <LayoutDashboard size={18} />,
  },
  { label: "Utilisateurs", to: "/admin/users", icon: <Users size={18} /> },
  {
    label: "Flash Crédits",
    to: "/admin/credits",
    icon: <CreditCard size={18} />,
  },
  { label: "Alertes", to: "/admin/alerts", icon: <Bell size={18} /> },
  { label: "KPIs", to: "/admin/kpis", icon: <BarChart3 size={18} /> },
  {
    label: "Intelligence Artificielle",
    to: "/admin/ml",
    icon: <Brain size={18} />,
  },
];

const superAdminNav = [
  { label: "Feature Flags", to: "/admin/flags", icon: <Flag size={18} /> },
  {
    label: "Configuration",
    to: "/admin/config",
    icon: <Settings2 size={18} />,
  },
  { label: "Admins", to: "/admin/admins", icon: <UserCog size={18} /> },
  { label: "Audit", to: "/admin/audit", icon: <ClipboardList size={18} /> },
];

export const AdminSidebar: React.FC = () => {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const isSuperAdmin = user?.role === "ROLE_SUPER_ADMIN";

  const handleLogout = () => {
    logout();
    navigate("/admin/login");
  };

  return (
    <aside className="w-64 min-h-screen bg-surface flex flex-col border-r border-white/[0.06]">
      {/* Header */}
      <div className="px-6 py-6 border-b border-white/[0.06]">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-purple to-primary flex items-center justify-center text-white flex-shrink-0">
            <ShieldCheck size={18} />
          </div>
          <div>
            <p className="font-bold text-white text-sm">FlowGuard Admin</p>
            <p className="text-text-muted text-xs">Backoffice</p>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {adminNav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}
          >
            {item.icon}
            <span className="text-sm font-medium">{item.label}</span>
          </NavLink>
        ))}

        {/* Super-admin section */}
        <div className="pt-4 pb-1 px-3">
          <p className="text-xs font-semibold text-text-muted uppercase tracking-wider">
            Super Admin
          </p>
        </div>

        {superAdminNav.map((item) =>
          isSuperAdmin ? (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `nav-item ${isActive ? "active" : ""}`
              }
            >
              {item.icon}
              <span className="text-sm font-medium">{item.label}</span>
            </NavLink>
          ) : (
            <div
              key={item.to}
              title="Accès réservé au Super Admin"
              className="nav-item opacity-40 cursor-not-allowed select-none"
            >
              <span className="opacity-60">{item.icon}</span>
              <span className="text-sm font-medium">{item.label}</span>
              <Lock size={12} className="ml-auto opacity-60" />
            </div>
          ),
        )}
      </nav>

      {/* Footer */}
      <div className="px-3 py-4 border-t border-white/[0.06] space-y-1">
        {/* Back to app */}
        <Link to="/" className="nav-item text-text-secondary hover:text-white">
          <ChevronLeft size={18} />
          <span className="text-sm font-medium">Retour à l'app</span>
        </Link>

        {/* User info */}
        <div className="px-4 py-2">
          <div className="flex items-center justify-between mb-0.5">
            <p className="text-xs font-medium text-white truncate">
              {user?.firstName} {user?.lastName}
            </p>
            {user?.role && (
              <span
                className={`text-[10px] font-semibold px-1.5 py-0.5 rounded flex-shrink-0 ml-2 ${
                  ROLE_COLOR[user.role] ?? "bg-white/10 text-text-secondary"
                }`}
              >
                {ROLE_BADGE[user.role] ?? user.role}
              </span>
            )}
          </div>
          <p className="text-xs text-text-muted truncate">{user?.email}</p>
        </div>

        <button
          onClick={handleLogout}
          className="nav-item w-full text-danger/70 hover:text-danger hover:bg-danger/10"
        >
          <LogOut size={18} />
          <span className="text-sm font-medium">Déconnexion</span>
        </button>
      </div>
    </aside>
  );
};
