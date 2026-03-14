import React from "react";

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  (
    { label, error, hint, leftIcon, rightIcon, className = "", id, ...props },
    ref,
  ) => {
    const inputId = id ?? label?.toLowerCase().replace(/\s+/g, "-");
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium text-text-secondary"
          >
            {label}
          </label>
        )}
        <div className="relative">
          {leftIcon && (
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted">
              {leftIcon}
            </div>
          )}
          <input
            ref={ref}
            id={inputId}
            {...props}
            className={`
              w-full h-11 rounded-xl border bg-white/[0.04] text-white placeholder:text-text-muted
              transition-all duration-200 text-sm
              focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary/50
              disabled:opacity-50 disabled:cursor-not-allowed
              ${error ? "border-danger/60" : "border-white/10"}
              ${leftIcon ? "pl-10" : "pl-4"}
              ${rightIcon ? "pr-10" : "pr-4"}
              ${className}
            `}
          />
          {rightIcon && (
            <div className="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted">
              {rightIcon}
            </div>
          )}
        </div>
        {error && <p className="text-danger text-xs">{error}</p>}
        {hint && !error && <p className="text-text-muted text-xs">{hint}</p>}
      </div>
    );
  },
);
Input.displayName = "Input";

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: { value: string; label: string }[];
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, options, className = "", id, ...props }, ref) => {
    const selectId = id ?? label?.toLowerCase().replace(/\s+/g, "-");
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={selectId}
            className="text-sm font-medium text-text-secondary"
          >
            {label}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          {...props}
          className={`
            w-full h-11 rounded-xl border bg-surface text-white
            transition-all duration-200 text-sm px-4
            focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary/50
            disabled:opacity-50 disabled:cursor-not-allowed
            ${error ? "border-danger/60" : "border-white/10"}
            ${className}
          `}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value} className="bg-surface">
              {o.label}
            </option>
          ))}
        </select>
        {error && <p className="text-danger text-xs">{error}</p>}
      </div>
    );
  },
);
Select.displayName = "Select";
