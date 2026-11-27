package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Login account. The password is only ever stored as a BCrypt hash. */
@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUtilisateur;

    /** Login: the email for accounts created by sign-up, "student" / "admin" for the demo accounts. */
    @Column(nullable = false, unique = true, length = 50)
    private String identifiant;

    /** BCrypt hash (60 characters), never the password itself. */
    @Column(nullable = false, length = 100)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RoleUtilisateur role;

    @Column(nullable = false)
    private LocalDateTime dateCreation;
}
