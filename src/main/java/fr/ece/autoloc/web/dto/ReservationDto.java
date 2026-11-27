package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class ReservationDto {

    private ReservationDto() {
    }

    public record Request(
            @NotNull Long clientId,
            @NotNull Long vehiculeId,
            @NotNull LocalDate dateDebut,
            @NotNull LocalDate dateFin) {
    }

    /**
     * Reservation made by a customer from his own space.
     * Either clientId (one of the account's profiles) or nouveauClient (a profile created on the fly) is given.
     */
    public record ClientRequest(
            Long clientId,
            @Valid ClientDto.Request nouveauClient,
            @NotNull Long vehiculeId,
            @NotNull LocalDate dateDebut,
            @NotNull LocalDate dateFin) {
    }

    public record StatutRequest(@NotNull StatutReservation statut) {
    }

    public record Response(
            Long id,
            LocalDate dateDebut,
            LocalDate dateFin,
            long nombreJours,
            StatutReservation statut,
            Long clientId,
            String clientNom,
            Long vehiculeId,
            String vehiculeLibelle,
            BigDecimal montantEstime,
            Long contratId) {

        public static Response from(Reservation r) {
            Client client = r.getClient();
            Vehicule vehicule = r.getVehicule();
            long jours = ReservationDto.nombreJours(r.getDateDebut(), r.getDateFin());
            return new Response(
                    r.getIdReservation(),
                    r.getDateDebut(),
                    r.getDateFin(),
                    jours,
                    r.getStatut(),
                    client == null ? null : client.getIdClient(),
                    client == null ? null : client.getPrenom() + " " + client.getNom(),
                    vehicule == null ? null : vehicule.getIdVehicule(),
                    vehicule == null ? null : ReservationDto.libelle(vehicule),
                    vehicule == null ? null : vehicule.getTarifJournalier().multiply(BigDecimal.valueOf(jours)),
                    r.getContrat() == null ? null : r.getContrat().getIdContrat());
        }
    }

    /** Number of rental days, first and last day included. */
    public static long nombreJours(LocalDate debut, LocalDate fin) {
        return ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    public static String libelle(Vehicule v) {
        return v.getMarque() + " " + v.getModele() + " (" + v.getImmatriculation() + ")";
    }
}
