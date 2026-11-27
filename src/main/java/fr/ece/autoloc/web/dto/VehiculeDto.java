package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class VehiculeDto {

    private VehiculeDto() {
    }

    public record Request(
            @NotBlank @Size(max = 20) String immatriculation,
            @NotBlank @Size(max = 50) String marque,
            @NotBlank @Size(max = 50) String modele,
            @NotNull CategorieVehicule categorie,
            @NotNull @DecimalMin("0.01") BigDecimal tarifJournalier,
            @NotNull StatutVehicule statut,
            Long agenceId) {
    }

    public record Response(
            Long id,
            String immatriculation,
            String marque,
            String modele,
            CategorieVehicule categorie,
            BigDecimal tarifJournalier,
            StatutVehicule statut,
            Long agenceId,
            String agenceNom,
            String photoUrl) {

        /** photoUrl is null when the vehicle has no photo. */
        public static Response from(Vehicule v, String photoUrl) {
            Agence agence = v.getAgence();
            return new Response(
                    v.getIdVehicule(),
                    v.getImmatriculation(),
                    v.getMarque(),
                    v.getModele(),
                    v.getCategorie(),
                    v.getTarifJournalier(),
                    v.getStatut(),
                    agence == null ? null : agence.getIdAgence(),
                    agence == null ? null : agence.getNom(),
                    photoUrl);
        }
    }
}
