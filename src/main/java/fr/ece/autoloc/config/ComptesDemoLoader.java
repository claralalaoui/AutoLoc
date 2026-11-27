package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.RoleUtilisateur;
import fr.ece.autoloc.service.CompteService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the two classroom accounts in MySQL if they do not exist yet,
 * so the Postman tests of the security exercise keep working. Classroom only: never real passwords.
 */
@Component
@RequiredArgsConstructor
public class ComptesDemoLoader implements CommandLineRunner {

    private final CompteService compteService;

    @Override
    public void run(String... args) {
        compteService.creerSiAbsent("student", "student123", RoleUtilisateur.USER);
        compteService.creerSiAbsent("admin", "admin123", RoleUtilisateur.ADMIN);
    }
}
