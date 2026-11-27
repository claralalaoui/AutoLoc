package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up form: the account (email + password) and the first driver profile.
 * The role is never part of the request: a new account is always a customer (USER).
 */
public record InscriptionDto(
        @NotBlank(message = "L'email est obligatoire.")
        @Size(max = 50, message = "L'email doit faire 50 caractères maximum.")
        @Pattern(regexp = ClientDto.EMAIL_REGEX, message = "Format d'email invalide.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "Le mot de passe doit contenir au moins 8 caractères, dont une lettre et un chiffre.")
        String motDePasse,

        @NotBlank(message = "Le prénom est obligatoire.") @Size(max = 50) String prenom,

        @NotBlank(message = "Le nom est obligatoire.") @Size(max = 50) String nom,

        @Size(max = 20)
        @Pattern(regexp = ClientDto.TELEPHONE_REGEX, message = "Numéro de téléphone invalide.")
        String telephone,

        @NotBlank(message = "Le numéro de permis est obligatoire.")
        @Pattern(regexp = ClientDto.PERMIS_REGEX,
                message = "Le numéro de permis doit contenir 8 à 15 lettres ou chiffres, sans caractère spécial.")
        String numPermis) {

    /** The first driver profile of the account. */
    public ClientDto.Request profil() {
        return new ClientDto.Request(nom, prenom, email, telephone, numPermis);
    }
}
