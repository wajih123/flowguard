import React from "react";
import { NavLink, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  TrendingUp,
  Bell,
  Zap,
  ArrowRightLeft,
  PieChart,
  GitBranch,
  Building2,
  LogOut,
  Users,
  Star,
  Shield,
  Settings,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useAlertStore } from "@/store/alertStore";
import type { Role } from "@/domain/User";

interface NavItem {
  label: string;
  to: string;
  icon: React.ReactNode;
  roles?: Role[];
}

const navItems: NavItem[] = [
  {
    label: "Tableau de bord",
    to: "/dashboard",
    icon: <LayoutDashboard size={17} />,
  },
  { label: "Prévisions", to: "/forecast", icon: <TrendingUp size={17} /> },
  { label: "Alertes", to: "/alerts", icon: <Bell size={17} /> },
  {
    label: "Transactions",
    to: "/transactions",
    icon: <ArrowRightLeft size={17} />,
  },
  { label: "Analyses", to: "/spending", icon: <PieChart size={17} /> },
  {
    label: "Scénarios",
    to: "/scenarios",
    icon: <GitBranch size={17} />,
    roles: ["ROLE_BUSINESS"],
  },
  {
    label: "Réserve",
    to: "/flash-credit",
    icon: <Zap size={17} />,
    roles: ["ROLE_USER"],
  },
  { label: "Banque", to: "/bank-connect", icon: <Building2 size={17} /> },
  {
    label: "Équipe",
    to: "/team",
    icon: <Users size={17} />,
    roles: ["ROLE_BUSINESS"],
  },
  { label: "Abonnement", to: "/subscription", icon: <Star size={17} /> },
  {
    label: "Admin",
    to: "/admin",
    icon: <Shield size={17} />,
    roles: ["ROLE_ADMIN", "ROLE_SUPER_ADMIN"],
  },
];

export const Sidebar: React.FC = () => {
  const { user, logout } = useAuthStore();
  const unreadCount = useAlertStore((s) => s.unreadCount);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const visibleItems = navItems.filter(
    (item) => !item.roles || (user?.role && item.roles.includes(user.role)),
  );

  return (
    <aside
      className="w-60 min-h-screen flex flex-col border-r"
      style={{
        background: "var(--color-bg-secondary)",
        borderColor: "var(--color-border)",
      }}
    >
      {/* Logo */}
      <div
        className="px-5 py-5 border-b"
        style={{ borderColor: "var(--color-border)" }}
      >
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-primary flex items-center justify-center text-white shadow-glow flex-shrink-0">
            <Zap size={18} fill="currentColor" />
          </div>
          <div>
            <p
              className="font-bold text-white text-sm"
              style={{ fontFamily: "var(--font-display)" }}
            >
              FlowGuard
            </p>
            <p className="text-text-muted text-xs">
              {user?.role === "ROLE_BUSINESS" ? "Pro" : "Trésorerie IA"}
            </p>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-3 space-y-0.5 overflow-y-auto">
        {visibleItems.map((item) => {
          const badgeCount = item.to === "/alerts" ? unreadCount : undefined;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `nav-item ${isActive ? "active" : ""}`
              }
            >
              <span className="flex-shrink-0">{item.icon}</span>
              <span className="flex-1 text-sm">{item.label}</span>
              {badgeCount ? (
                <span className="min-w-[20px] h-5 bg-danger rounded-full flex items-center justify-center text-white text-[10px] font-bold px-1">
                  {badgeCount > 9 ? "9+" : badgeCount}
                </span>
              ) : null}
            </NavLink>
          );
        })}
      </nav>

      {/* User + actions */}
      <div
        className="px-3 py-3 border-t space-y-0.5"
        style={{ borderColor: "var(--color-border)" }}
      >
        <NavLink
          to="/profile"
          className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}
        >
          <div className="w-7 h-7 rounded-full bg-primary/15 border border-primary/30 flex items-center justify-center text-primary text-xs font-bold flex-shrink-0">
            {user?.firstName?.[0]}
            {user?.lastName?.[0]}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-white truncate">
              {user?.firstName} {user?.lastName}
            </p>
            <p className="text-xs text-text-muted truncate">{user?.email}</p>
          </div>
        </NavLink>

        <NavLink
          to="/settings"
          className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}
        >
          <Settings size={17} />
          <span className="text-sm">Paramètres</span>
        </NavLink>

        <button
          onClick={handleLogout}
          className="nav-item w-full text-left text-danger/60 hover:text-danger hover:bg-danger/10"
        >
          <LogOut size={17} />
          <span className="text-sm">Déconnexion</span>
        </button>
      </div>
    </aside>
  );
};
