package com.flowguard.service;

import com.flowguard.dto.SectorBenchmarkDto;
import com.flowguard.dto.UserBenchmarkDto;
import com.flowguard.repository.SectorBenchmarkRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SectorBenchmarkService {

    @Inject SectorBenchmarkRepository benchmarkRepo;
    @Inject AccountService accountService;
    @Inject SpendingAnalysisService spendingAnalysisService;
    @Inject InvoiceService invoiceService;
    @Inject ForecastAccuracyService forecastAccuracyService;

    public List<SectorBenchmarkDto> getForSector(String sector, String companySize) {
        return benchmarkRepo.findBySectorAndSize(sector, companySize)
                .stream().map(SectorBenchmarkDto::from).toList();
    }

    public List<String> getAvailableSectors() {
        return benchmarkRepo.findDistinctSectors();
    }

    /**
     * Compare a user's actual metrics to the sector benchmark percentiles.
     */
    public List<UserBenchmarkDto> compareUser(UUID userId, String sector, String companySize) {
        List<SectorBenchmarkDto> benchmarks = getForSector(sector, companySize);

        return benchmarks.stream().map(b -> {
            BigDecimal userValue = getUserMetricValue(userId, b.metricName());
            if (userValue == null) return null;

            String band = computeBand(userValue, b.p25(), b.p50(), b.p75());
            String insight = buildInsight(b.metricName(), band, userValue, b.unit());

            return new UserBenchmarkDto(
                    sector, companySize, b.metricName(), userValue, b.unit(),
                    b.p25(), b.p50(), b.p75(), band, insight
            );
        }).filter(dto -> dto != null).toList();
    }

    private BigDecimal getUserMetricValue(UUID userId, String metricName) {
        return switch (metricName) {
            case "monthly_revenue" -> invoiceService.getOutstandingTotal(userId);
            case "avg_invoice_size" -> invoiceService.getOutstandingTotal(userId);
            default -> null;
        };
    }

    private String computeBand(BigDecimal value, BigDecimal p25, BigDecimal p50, BigDecimal p75) {
        if (value == null || p25 == null || p75 == null) return "UNKNOWN";
        if (value.compareTo(p25) < 0) return "BOTTOM_25";
        if (value.compareTo(p50) < 0) return "Q1_Q2";
        if (value.compareTo(p75) < 0) return "Q2_Q3";
        return "TOP_25";
    }

    private String buildInsight(String metric, String band, BigDecimal value, String unit) {
        return switch (band) {
            case "TOP_25" -> "Votre " + formatMetric(metric) + " dépasse 75% des entreprises de votre secteur. Excellent !";
            case "Q2_Q3"  -> "Votre " + formatMetric(metric) + " est dans la moitié supérieure de votre secteur.";
            case "Q1_Q2"  -> "Votre " + formatMetric(metric) + " est légèrement en dessous de la médiane sectorielle.";
            case "BOTTOM_25" -> "Votre " + formatMetric(metric) + " est dans le quart inférieur du secteur. Des optimisations sont possibles.";
            default -> "Données insuffisantes pour comparer ce métrique.";
        };
    }

    private String formatMetric(String metricName) {
        return switch (metricName) {
            case "monthly_revenue"          -> "chiffre d'affaires mensuel";
            case "runway_days"              -> "visibilité de trésorerie";
            case "avg_invoice_size"         -> "ticket moyen de facture";
            case "avg_payment_delay_days"   -> "délai de paiement moyen";
            case "burn_rate_pct"            -> "taux de burn";
            default -> metricName.replace("_", " ");
        };
    }
}
