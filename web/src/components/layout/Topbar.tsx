import React, { useEffect, useState } from "react";
import { Bell, Menu } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAlertStore } from "@/store/alertStore";

interface TopbarProps {
  title?: string;
  onMenuClick?: () => void;
}

export const Topbar: React.FC<TopbarProps> = ({ title, onMenuClick }) => {
  const unreadCount = useAlertStore((s) => s.unreadCount);
  const navigate = useNavigate();
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={`h-16 flex items-center justify-between px-6 sticky top-0 z-20 transition-all duration-200 ${
        scrolled
          ? "border-b border-white/[0.08] bg-background/80 backdrop-blur-xl"
          : "border-b border-white/[0.06]"
      }`}
    >
      <div className="flex items-center gap-4">
        {onMenuClick && (
          <button
            onClick={onMenuClick}
            className="p-2 rounded-lg text-text-secondary hover:text-white hover:bg-white/[0.06] transition-colors lg:hidden"
          >
            <Menu size={20} />
          </button>
        )}
        {title && (
          <h1
            className="text-white font-semibold"
            style={{ fontFamily: "var(--font-display)" }}
          >
            {title}
          </h1>
        )}
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate("/alerts")}
          className="relative p-2 rounded-xl text-text-secondary hover:text-white hover:bg-white/[0.06] transition-colors"
          aria-label={
            unreadCount > 0 ? `${unreadCount} alertes non lues` : "Alertes"
          }
        >
          <Bell size={20} />
          {unreadCount > 0 && (
            <span className="absolute top-1 right-1 w-4 h-4 bg-danger rounded-full text-white text-[10px] flex items-center justify-center font-bold animate-pulse-dot">
              {unreadCount > 9 ? "9+" : unreadCount}
            </span>
          )}
        </button>
      </div>
    </header>
  );
};
