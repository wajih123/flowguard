import React, { useState, useCallback } from "react";
import { Loader2 } from "lucide-react";

export type ButtonVariant =
  | "primary"
  | "gradient"
  | "secondary"
  | "danger"
  | "danger-solid"
  | "ghost"
  | "outline"
  | "success";
export type ButtonSize = "xs" | "sm" | "md" | "lg" | "xl";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  /** Adds a 500ms delay before onClick fires (anti-double-click for financial actions) */
  confirmDelay?: boolean;
  fullWidth?: boolean;
}

const variants: Record<ButtonVariant, string> = {
  primary:
    "bg-primary text-white hover:bg-primary-dark shadow-glow hover:shadow-none active:scale-[0.98]",
  gradient:
    "bg-gradient-primary text-white shadow-glow hover:shadow-glow-strong active:scale-[0.98]",
  secondary:
    "bg-surface-light text-white border border-white/10 hover:bg-surface-hover",
  danger: "bg-danger/10 text-danger border border-danger/20 hover:bg-danger/20",
  "danger-solid":
    "bg-gradient-danger text-white hover:opacity-90 active:scale-[0.98]",
  ghost: "text-text-secondary hover:text-white hover:bg-white/[0.06]",
  outline:
    "border border-primary/40 text-primary hover:bg-primary/10 hover:border-primary/70",
  success:
    "bg-success/10 text-success border border-success/20 hover:bg-success/20",
};

const sizes: Record<ButtonSize, string> = {
  xs: "h-7 px-2.5 text-xs gap-1 rounded-lg",
  sm: "h-8 px-3 text-xs gap-1.5 rounded-xl",
  md: "h-10 px-4 text-sm gap-2 rounded-xl",
  lg: "h-12 px-6 text-base gap-2.5 rounded-xl",
  xl: "h-14 px-8 text-base gap-3 rounded-2xl",
};

export const Button: React.FC<ButtonProps> = ({
  variant = "primary",
  size = "md",
  isLoading,
  leftIcon,
  rightIcon,
  children,
  disabled,
  className = "",
  confirmDelay = false,
  fullWidth = false,
  onClick,
  ...props
}) => {
  const [delaying, setDelaying] = useState(false);

  const handleClick = useCallback(
    (e: React.MouseEvent<HTMLButtonElement>) => {
      if (!onClick) return;
      if (confirmDelay) {
        setDelaying(true);
        setTimeout(() => {
          setDelaying(false);
          onClick(e);
        }, 500);
      } else {
        onClick(e);
      }
    },
    [onClick, confirmDelay],
  );

  return (
    <button
      {...props}
      onClick={handleClick}
      disabled={disabled || isLoading || delaying}
      className={`
        inline-flex items-center justify-center font-semibold
        transition-all duration-150
        focus-visible:outline-none focus-visible:ring-2
        focus-visible:ring-primary/60 focus-visible:ring-offset-2
        focus-visible:ring-offset-background
        disabled:opacity-50 disabled:cursor-not-allowed
        ${fullWidth ? "w-full" : ""}
        ${variants[variant]} ${sizes[size]} ${className}
      `}
    >
      {isLoading || delaying ? (
        <Loader2 className="animate-spin" size={size === "xs" ? 12 : 16} />
      ) : (
        leftIcon
      )}
      {children}
      {!isLoading && !delaying && rightIcon}
    </button>
  );
};
