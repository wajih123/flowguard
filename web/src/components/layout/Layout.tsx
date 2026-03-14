import React from "react";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

interface LayoutProps {
  children: React.ReactNode;
  title?: string;
}

export const Layout: React.FC<LayoutProps> = ({ children, title }) => (
  <div className="flex min-h-screen bg-background">
    <Sidebar />
    <div className="flex-1 flex flex-col min-w-0">
      <Topbar title={title} />
      <main className="flex-1 p-6 overflow-auto animate-fade-in">
        {children}
      </main>
    </div>
  </div>
);
