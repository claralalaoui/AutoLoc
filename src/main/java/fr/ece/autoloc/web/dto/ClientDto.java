package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.Client;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class ClientDto {

    private ClientDto() {
    }

    public record Request(
            @NotBlank @Size(max = 50) String nom,
            @NotBlank @Size(max = 50) String prenom,
            @NotBlank @Email @Size(max = 100) String email,
            @Size(max = 20) String telephone,
            @NotBlank @Size(max = 30) String numPermis) {
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
