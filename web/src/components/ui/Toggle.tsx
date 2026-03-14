import React, { useId } from "react";

interface ToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  description?: string;
  disabled?: boolean;
  size?: "sm" | "md";
}

export const Toggle: React.FC<ToggleProps> = ({
  checked,
  onChange,
  label,
  description,
  disabled = false,
  size = "md",
}) => {
  const id = useId();

  const trackSize = size === "sm" ? "w-8 h-4" : "w-11 h-6";
  const thumbSize = size === "sm" ? "w-3 h-3" : "w-4 h-4";
  const translate = size === "sm" ? "translate-x-4" : "translate-x-5";

  return (
    <div className="flex items-start gap-3">
      <button
        id={id}
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => !disabled && onChange(!checked)}
        className={`
          relative inline-flex shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out
          ${trackSize}
          ${checked ? "bg-primary" : "bg-white/20"}
          ${disabled ? "opacity-50 cursor-not-allowed" : ""}
          focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60
        `}
      >
        <span
          className={`
            pointer-events-none inline-block rounded-full bg-white shadow-lg transform transition duration-200 ease-in-out
            ${thumbSize}
            mt-1 ml-1
            ${checked ? translate : "translate-x-0"}
          `}
        />
      </button>

      {(label || description) && (
        <label
          htmlFor={id}
          className={`cursor-pointer ${disabled ? "opacity-50" : ""}`}
        >
          {label && <p className="text-sm font-medium text-white">{label}</p>}
          {description && (
            <p className="text-xs text-text-secondary mt-0.5">{description}</p>
          )}
        </label>
      )}
    </div>
  );
};
