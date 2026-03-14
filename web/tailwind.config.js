/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        /* ── Backgrounds ─────────────────────────────── */
        background: "#0B1437",
        surface: "#111C44",
        "surface-light": "#1B254B",
        "surface-hover": "#21305A",
        "surface-elevated": "#243060",
        /* Light mode surfaces */
        "bg-light": "#F8FAFF",
        "bg-light-card": "#FFFFFF",
        "bg-light-muted": "#F0F4FF",
        /* ── Brand / Primary ───────────────────────── */
        primary: "#06B6D4",
        "primary-dark": "#0891B2",
        "primary-light": "#67E8F9",
        "primary-dim": "rgba(6,182,212,0.15)",
        "primary-glow": "rgba(6,182,212,0.30)",
        /* ── Semantic ──────────────────────────────── */
        success: "#10B981",
        "success-light": "#D1FAE5",
        warning: "#F59E0B",
        "warning-light": "#FEF3C7",
        danger: "#EF4444",
        "danger-light": "#FEE2E2",
        info: "#3B82F6",
        purple: "#8B5CF6",
        /* ── Text ──────────────────────────────────── */
        "text-primary": "#FFFFFF",
        "text-secondary": "#A0AEC0",
        "text-muted": "#4A5568",
        "text-inverted": "#0B1437",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
        display: ["DM Sans", "system-ui", "sans-serif"],
        numeric: ["DM Mono", "ui-monospace", "monospace"],
      },
      fontSize: {
        "2xs": ["0.625rem", { lineHeight: "1rem" }],
      },
      borderRadius: {
        sm: "6px",
        md: "12px",
        lg: "16px",
        xl: "20px",
        "2xl": "24px",
        "3xl": "32px",
        full: "9999px",
      },
      boxShadow: {
        card: "0 4px 24px rgba(0,0,0,0.25)",
        modal: "0 24px 64px rgba(0,0,0,0.50)",
        glow: "0 0 32px rgba(6,182,212,0.20)",
        "glow-strong": "0 0 20px rgba(6,182,212,0.40)",
        "glow-danger": "0 0 32px rgba(239,68,68,0.20)",
        "glow-success": "0 0 20px rgba(16,185,129,0.20)",
      },
      backgroundImage: {
        "gradient-primary": "linear-gradient(135deg,#06B6D4 0%,#3B82F6 100%)",
        "gradient-danger": "linear-gradient(135deg,#EF4444 0%,#F97316 100%)",
        "gradient-success": "linear-gradient(135deg,#10B981 0%,#06B6D4 100%)",
        "gradient-card": "linear-gradient(145deg,#1B254B 0%,#111C44 100%)",
        "gradient-glow":
          "radial-gradient(ellipse at top,rgba(6,182,212,0.15) 0%,transparent 70%)",
      },
      animation: {
        "fade-in": "fadeIn 0.3s ease-out",
        "slide-up": "slideUp 0.4s ease-out",
        "slide-down": "slideDown 0.3s cubic-bezier(0.34,1.56,0.64,1)",
        shimmer: "shimmer 1.5s ease-in-out infinite",
        "pulse-dot": "pulseDot 2s ease-in-out infinite",
        "draw-check": "drawCheck 0.6s ease-out forwards",
        "count-up": "fadeIn 0.8s ease-out",
        "gauge-fill": "gaugeFill 0.8s ease-out forwards",
      },
      keyframes: {
        fadeIn: { from: { opacity: "0" }, to: { opacity: "1" } },
        slideUp: {
          from: { transform: "translateY(20px)", opacity: "0" },
          to: { transform: "translateY(0)", opacity: "1" },
        },
        slideDown: {
          from: { transform: "translateY(-16px)", opacity: "0" },
          to: { transform: "translateY(0)", opacity: "1" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
        pulseDot: {
          "0%,100%": { opacity: "1", transform: "scale(1)" },
          "50%": { opacity: "0.5", transform: "scale(1.15)" },
        },
        drawCheck: {
          from: { strokeDashoffset: "100" },
          to: { strokeDashoffset: "0" },
        },
        gaugeFill: {
          from: { strokeDashoffset: "var(--gauge-max)" },
          to: { strokeDashoffset: "var(--gauge-value)" },
        },
      },
      transitionTimingFunction: {
        spring: "cubic-bezier(0.34,1.56,0.64,1)",
      },
    },
  },
  plugins: [],
};
