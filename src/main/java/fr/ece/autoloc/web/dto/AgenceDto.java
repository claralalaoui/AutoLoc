package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.Agence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AgenceDto {

    private AgenceDto() {
    }

    public record Request(
            @NotBlank @Size(max = 100) String nom,
            @NotBlank @Size(max = 50) String ville,
            @NotBlank @Size(max = 255) String adresse,
            @Size(max = 20) String telephone) {
    }

    public record Response(
            Long id,
            String nom,
            String ville,
            String adresse,
            String telephone,
            int nombreVehicules) {

        public static Response from(Agence a) {
            return new Response(
                    a.getIdAgence(),
                    a.getNom(),
                    a.getVille(),
                    a.getAdresse(),
                    a.getTelephone(),
                    a.getVehicules().size());
        }
    }
}
