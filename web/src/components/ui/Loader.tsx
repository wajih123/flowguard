import React from "react";
import { Loader2 } from "lucide-react";

interface LoaderProps {
  size?: "sm" | "md" | "lg";
  fullScreen?: boolean;
  text?: string;
}

const sizes = { sm: 16, md: 24, lg: 40 };

export const Loader: React.FC<LoaderProps> = ({
  size = "md",
  fullScreen,
  text,
}) => {
  const icon = (
    <Loader2 className="animate-spin text-primary" size={sizes[size]} />
  );

  if (fullScreen) {
    return (
      <div className="fixed inset-0 bg-background flex flex-col items-center justify-center gap-4">
        {icon}
        {text && <p className="text-text-secondary text-sm">{text}</p>}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12">
      {icon}
      {text && <p className="text-text-secondary text-sm">{text}</p>}
    </div>
  );
};
