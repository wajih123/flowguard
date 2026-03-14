import React from "react";
import { AdminSidebar } from "./AdminSidebar";

interface AdminLayoutProps {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  action?: React.ReactNode;
}

export const AdminLayout: React.FC<AdminLayoutProps> = ({
  children,
  title,
  subtitle,
  action,
}) => (
  <div className="flex min-h-screen bg-background">
    <AdminSidebar />
    <div className="flex-1 flex flex-col min-w-0">
      {(title || action) && (
        <header className="h-16 border-b border-white/[0.06] flex items-center justify-between px-6 flex-shrink-0">
          <div>
            {title && <h1 className="text-white font-semibold">{title}</h1>}
            {subtitle && (
              <p className="text-text-secondary text-xs">{subtitle}</p>
            )}
          </div>
          {action}
        </header>
      )}
      <main className="flex-1 p-6 overflow-auto animate-fade-in">
        {children}
      </main>
    </div>
  </div>
);
