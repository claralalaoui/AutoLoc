package fr.ece.autoloc.web.dto;

import java.math.BigDecimal;

public record DashboardDto(
        long vehicules,
        long vehiculesDisponibles,
        long vehiculesLoues,
        long vehiculesEnMaintenance,
        long agences,
        long clients,
        long reservationsActives,
        long contrats,
        BigDecimal totalEncaisse) {
}
