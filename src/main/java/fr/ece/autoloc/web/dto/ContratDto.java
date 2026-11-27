package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ContratDto {

    private ContratDto() {
    }

    public record PaiementRequest(
            @NotNull @DecimalMin("0.01") BigDecimal montant,
            @NotNull ModePaiement modePaiement) {
    }

    public record PaiementResponse(
            Long id,
            BigDecimal montant,
            LocalDate datePaiement,
            ModePaiement modePaiement) {

        public static PaiementResponse from(Paiement p) {
            return new PaiementResponse(p.getIdPaiement(), p.getMontant(), p.getDatePaiement(), p.getModePaiement());
        }
    }

    public record Response(
            Long id,
            LocalDate dateSignature,
            BigDecimal montantTotal,
            boolean valide,
            Long reservationId,
            String clientNom,
            String vehiculeLibelle,
            BigDecimal totalPaye,
            BigDecimal resteAPayer,
            List<PaiementResponse> paiements) {

        public static Response from(Contrat c) {
            Reservation r = c.getReservation();
            BigDecimal totalPaye = c.getPaiements().stream()
                    .map(Paiement::getMontant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Response(
                    c.getIdContrat(),
                    c.getDateSignature(),
                    c.getMontantTotal(),
                    c.isValide(),
                    r == null ? null : r.getIdReservation(),
                    r == null || r.getClient() == null ? null : r.getClient().getPrenom() + " " + r.getClient().getNom(),
                    r == null || r.getVehicule() == null ? null : ReservationDto.libelle(r.getVehicule()),
                    totalPaye,
                    c.getMontantTotal().subtract(totalPaye).max(BigDecimal.ZERO),
                    c.getPaiements().stream().map(PaiementResponse::from).toList());
        }
    }
}
