package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.Client;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class ClientDto {

    /** name@domain.tld: stricter than @Email, which also accepts addresses without a domain extension. */
    public static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /** Optional; digits with an optional leading +, spaces, dots, dashes or brackets. */
    public static final String TELEPHONE_REGEX = "^\\+?[0-9 .()-]{6,20}$";

    /** 8 to 15 letters or digits, no space and no special character. */
    public static final String PERMIS_REGEX = "^[A-Za-z0-9]{8,15}$";

    private ClientDto() {
    }

    public record Request(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 50) String nom,
            @NotBlank(message = "Le prénom est obligatoire.") @Size(max = 50) String prenom,
            @NotBlank(message = "L'email est obligatoire.") @Size(max = 100)
            @Pattern(regexp = EMAIL_REGEX, message = "Format d'email invalide.") String email,
            @Size(max = 20) @Pattern(regexp = TELEPHONE_REGEX, message = "Numéro de téléphone invalide.") String telephone,
            @NotBlank(message = "Le numéro de permis est obligatoire.")
            @Pattern(regexp = PERMIS_REGEX,
                    message = "Le numéro de permis doit contenir 8 à 15 lettres ou chiffres, sans caractère spécial.")
            String numPermis) {
    }

    public record Response(
            Long id,
            String nom,
            String prenom,
            String email,
            String telephone,
            String numPermis,
            LocalDate dateInscription) {

        public static Response from(Client c) {
            return new Response(
                    c.getIdClient(),
                    c.getNom(),
                    c.getPrenom(),
                    c.getEmail(),
                    c.getTelephone(),
                    c.getNumPermis(),
                    c.getDateInscription());
        }
    }
}
