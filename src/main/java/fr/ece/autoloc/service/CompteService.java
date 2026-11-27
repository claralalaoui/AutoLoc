package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.RoleUtilisateur;
import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.repository.UtilisateurRepository;
import fr.ece.autoloc.web.dto.InscriptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Accounts stored in MySQL. Spring Security uses this class (UserDetailsService)
 * to load the account and its role at each authenticated request.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompteService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientService clientService;

    @Override
    public UserDetails loadUserByUsername(String identifiant) {
        Utilisateur utilisateur = utilisateurRepository.findByIdentifiant(normaliser(identifiant))
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu"));
        return User.withUsername(utilisateur.getIdentifiant())
                .password(utilisateur.getMotDePasse())
                .roles(utilisateur.getRole().name())
                .build();
    }

    /**
     * Creates a customer account and its first driver profile, in one transaction:
     * if the profile is refused (licence already used...), the account is not created either.
     * Returns the login to use.
     */
    @Transactional
    public String inscrire(InscriptionDto request) {
        String email = normaliser(request.email());
        if (utilisateurRepository.existsByIdentifiant(email)) {
            throw new BusinessRuleException("Un compte existe déjà avec cet email. Connectez-vous.");
        }
        creer(email, request.motDePasse(), RoleUtilisateur.USER);
        clientService.creerPourCompte(email, request.profil());
        return email;
    }

    /** Used at startup to make sure the demo accounts exist. */
    @Transactional
    public void creerSiAbsent(String identifiant, String motDePasse, RoleUtilisateur role) {
        if (!utilisateurRepository.existsByIdentifiant(identifiant)) {
            creer(identifiant, motDePasse, role);
        }
    }

    private void creer(String identifiant, String motDePasse, RoleUtilisateur role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasse));
        utilisateur.setRole(role);
        utilisateur.setDateCreation(LocalDateTime.now());
        utilisateurRepository.save(utilisateur);
    }

    private static String normaliser(String identifiant) {
        return identifiant == null ? "" : identifiant.trim().toLowerCase(Locale.ROOT);
    }
}
