/**
 * Format a financial amount in French euros.
 * Ex: formatAmount(3240) → "3 240,00 €"
 * Ex: formatAmount(-230) → "-230,00 €"
 */
export function formatAmount(amount: number, currency = "EUR"): string {
  return new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/**
 * Format a date in full French.
 * Ex: formatDate('2026-03-18') → "18 mars 2026"
 */
export function formatDate(date: string | Date): string {
  return new Intl.DateTimeFormat("fr-FR", {
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(typeof date === "string" ? new Date(date) : date);
}

/**
 * Format a short date in French (day + month only).
 * Ex: formatDateShort('2026-03-18') → "18 mars"
 */
export function formatDateShort(date: string | Date): string {
  return new Intl.DateTimeFormat("fr-FR", {
    day: "numeric",
    month: "long",
  }).format(typeof date === "string" ? new Date(date) : date);
}

/**
 * Format amount for chart axes — compact, no decimals.
 * Ex: formatAmountCompact(3240) → "3 240 €"
 */
export function formatAmountCompact(amount: number, currency = "EUR"): string {
  return new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount);
}
