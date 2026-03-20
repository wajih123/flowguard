import React, { useState } from "react";

interface HelpTooltipProps {
  text: string;
  className?: string;
}

export function HelpTooltip({ text, className = "" }: HelpTooltipProps) {
  const [open, setOpen] = useState(false);
  return (
    <span className={`relative inline-flex shrink-0 ${className}`}>
      <button
        type="button"
        aria-label="En savoir plus"
        onClick={(e) => {
          e.stopPropagation();
          setOpen((v) => !v);
        }}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
        onBlur={() => setOpen(false)}
        className="w-[14px] h-[14px] rounded-full bg-white/[0.08] text-text-muted border border-white/[0.14] inline-flex items-center justify-center text-[9px] font-bold cursor-help hover:bg-white/[0.18] hover:text-white transition-colors leading-none"
      >
        ?
      </button>
      {open && (
        <span
          role="tooltip"
          className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-56 z-50 rounded-lg bg-gray-900 border border-white/[0.14] p-2.5 text-[11px] text-gray-300 shadow-2xl leading-relaxed pointer-events-none"
        >
          {text}
          <span className="absolute top-full left-1/2 -translate-x-1/2 border-[5px] border-transparent border-t-gray-900" />
        </span>
      )}
    </span>
  );
}
