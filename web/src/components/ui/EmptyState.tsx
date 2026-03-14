import React from "react";
import { InboxIcon } from "lucide-react";

interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  icon,
  action,
}) => (
  <div className="flex flex-col items-center justify-center py-16 gap-4 text-center">
    <div className="w-16 h-16 rounded-2xl bg-white/[0.04] flex items-center justify-center text-text-muted">
      {icon ?? <InboxIcon size={28} />}
    </div>
    <div>
      <p className="text-white font-medium">{title}</p>
      {description && (
        <p className="text-text-secondary text-sm mt-1 max-w-xs mx-auto">
          {description}
        </p>
      )}
    </div>
    {action}
  </div>
);
