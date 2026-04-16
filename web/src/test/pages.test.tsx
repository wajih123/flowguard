import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";

// Mock React Query
vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn().mockReturnValue({
    data: undefined,
    isLoading: false,
    isFetching: false,
    refetch: vi.fn(),
  }),
  useMutation: vi.fn().mockReturnValue({ mutate: vi.fn(), isPending: false }),
  useQueryClient: vi.fn().mockReturnValue({ invalidateQueries: vi.fn() }),
}));

// Mock react-router-dom (Link, Navigate, useNavigate, etc.)
vi.mock("react-router-dom", () => ({
  Link: ({ children, to }: any) => <a href={to}>{children}</a>,
  Navigate: () => null,
  useNavigate: vi.fn().mockReturnValue(vi.fn()),
  useLocation: vi.fn().mockReturnValue({ pathname: "/" }),
}));

// Mock Layout and UI primitives to avoid router/auth context deps
vi.mock("@/components/layout/Layout", () => ({
  Layout: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("@/components/ui/Card", () => ({
  Card: ({ children }: any) => <div>{children}</div>,
  CardHeader: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("@/components/ui/Button", () => ({
  Button: ({ children, label, onClick }: any) => (
    <button onClick={onClick}>{children ?? label}</button>
  ),
}));
vi.mock("@/components/ui/Loader", () => ({
  Loader: () => <div>Loading...</div>,
}));

// Mock API modules
vi.mock("@/api/invoices", () => ({
  invoicesApi: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn().mockResolvedValue({}),
    send: vi.fn().mockResolvedValue({}),
    markPaid: vi.fn().mockResolvedValue({}),
    cancel: vi.fn().mockResolvedValue({}),
    outstanding: vi.fn().mockResolvedValue(0),
  },
}));

vi.mock("@/api/budgets", () => ({
  budgetsApi: {
    vsActual: vi.fn().mockResolvedValue({ lines: [] }),
    upsert: vi.fn().mockResolvedValue({}),
    deleteLine: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("@/api/tax", () => ({
  taxApi: {
    list: vi.fn().mockResolvedValue([]),
    upcoming: vi.fn().mockResolvedValue([]),
    markPaid: vi.fn().mockResolvedValue({}),
    regenerate: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock("@/api/benchmarks", () => ({
  benchmarksApi: {
    get: vi.fn().mockResolvedValue({
      sector: "RETAIL",
      sizeCategory: "MICRO",
      currentBalance: 0,
      percentileBand: "P50",
      insights: [],
    }),
  },
}));

vi.mock("@/api/forecastAccuracy", () => ({
  forecastAccuracyApi: {
    list: vi.fn().mockResolvedValue([]),
    byHorizon: vi.fn().mockResolvedValue([]),
    summary: vi.fn().mockResolvedValue({
      averageAccuracyPct: 0,
      totalEntries: 0,
      reconciledEntries: 0,
    }),
  },
}));

vi.mock("@/api/payments", () => ({
  paymentsApi: {
    list: vi.fn().mockResolvedValue([]),
    initiate: vi.fn().mockResolvedValue({}),
    cancel: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock("@/api/accountant", () => ({
  accountantApi: {
    listGrants: vi.fn().mockResolvedValue([]),
    grantAccess: vi.fn().mockResolvedValue({}),
    revokeAccess: vi.fn().mockResolvedValue(undefined),
    downloadFec: vi.fn().mockResolvedValue(""),
  },
}));

vi.mock("@/hooks/useDashboard", () => ({
  useDashboard: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
}));

vi.mock("@/hooks/useForecast", () => ({
  useForecast: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
}));

vi.mock("@/store/authStore", () => ({
  useAuthStore: vi.fn().mockReturnValue({
    user: { firstName: "Test", role: "ROLE_USER" },
    isAuthenticated: false,
    isLoading: false,
  }),
}));

// ────────────────────────────────────────────────────────────
// Import pages after mocks are in place
// ────────────────────────────────────────────────────────────
import InvoicesPage from "../client/pages/InvoicesPage";
import BudgetPage from "../client/pages/BudgetPage";
import TaxPage from "../client/pages/TaxPage";
import BenchmarksPage from "../client/pages/BenchmarksPage";
import ForecastAccuracyPage from "../client/pages/ForecastAccuracyPage";
import PaymentsPage from "../client/pages/PaymentsPage";
import AccountantPage from "../client/pages/AccountantPage";
import LandingPage from "../pages/LandingPage";
import AssistantPage from "../client/pages/AssistantPage";

function renderPage(Page: React.ComponentType) {
  return render(<Page />);
}

describe("InvoicesPage", () => {
  it("renders without crashing", () => {
    renderPage(InvoicesPage);
  });
  it("shows invoices heading", () => {
    renderPage(InvoicesPage);
    expect(screen.getAllByText(/facture/i).length).toBeGreaterThan(0);
  });
});

describe("BudgetPage", () => {
  it("renders without crashing", () => {
    renderPage(BudgetPage);
  });
  it("shows budget heading", () => {
    renderPage(BudgetPage);
    expect(screen.getAllByText(/budget/i).length).toBeGreaterThan(0);
  });
});

describe("TaxPage", () => {
  it("renders without crashing", () => {
    renderPage(TaxPage);
  });
  it("shows tax heading", () => {
    renderPage(TaxPage);
    expect(screen.getAllByText(/fiscal|taxe|TVA/i).length).toBeGreaterThan(0);
  });
});

describe("BenchmarksPage", () => {
  it("renders without crashing", () => {
    renderPage(BenchmarksPage);
  });
  it("shows benchmarks heading", () => {
    renderPage(BenchmarksPage);
    expect(screen.getAllByText(/benchmark|secteur/i).length).toBeGreaterThan(0);
  });
});

describe("ForecastAccuracyPage", () => {
  it("renders without crashing", () => {
    renderPage(ForecastAccuracyPage);
  });
  it("shows accuracy heading", () => {
    renderPage(ForecastAccuracyPage);
    expect(screen.getAllByText(/vision|forecast/i).length).toBeGreaterThan(0);
  });
});

describe("PaymentsPage", () => {
  it("renders without crashing", () => {
    renderPage(PaymentsPage);
  });
  it("shows payments heading", () => {
    renderPage(PaymentsPage);
    expect(screen.getAllByText(/virement|paiement/i).length).toBeGreaterThan(0);
  });
});

describe("AccountantPage", () => {
  it("renders without crashing", () => {
    renderPage(AccountantPage);
  });
  it("shows accountant heading", () => {
    renderPage(AccountantPage);
    expect(
      screen.getAllByText(/comptable|expert|portail/i).length,
    ).toBeGreaterThan(0);
  });
});

describe("LandingPage", () => {
  it("renders without crashing", () => {
    renderPage(LandingPage);
  });
  it("shows FlowGuard brand", () => {
    renderPage(LandingPage);
    expect(screen.getAllByText(/FlowGuard/i).length).toBeGreaterThan(0);
  });
  it("shows hero headline", () => {
    renderPage(LandingPage);
    expect(screen.getAllByText(/trésorerie/i).length).toBeGreaterThan(0);
  });
  it("shows sign-in link", () => {
    renderPage(LandingPage);
    expect(screen.getAllByText(/connexion/i).length).toBeGreaterThan(0);
  });
  it("shows pricing section", () => {
    renderPage(LandingPage);
    expect(screen.getAllByText(/tarif|prix|free|pro/i).length).toBeGreaterThan(
      0,
    );
  });
});

describe("AssistantPage", () => {
  it("renders without crashing", () => {
    renderPage(AssistantPage);
  });
  it("shows AI advisor heading", () => {
    renderPage(AssistantPage);
    expect(screen.getAllByText(/conseiller|IA|assistant/i).length).toBeGreaterThan(0);
  });
  it("shows quick question chips", () => {
    renderPage(AssistantPage);
    expect(screen.getAllByText(/situation|améliorer|risque/i).length).toBeGreaterThan(0);
  });
  it("shows welcome message", () => {
    renderPage(AssistantPage);
    expect(screen.getAllByText(/bonjour|conseiller/i).length).toBeGreaterThan(0);
  });
});
