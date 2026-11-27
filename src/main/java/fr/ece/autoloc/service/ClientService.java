package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.web.dto.ClientDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;

    public List<ClientDto.Response> findAll() {
        return clientRepository.findAllByOrderByNomAscPrenomAsc().stream()
                .map(ClientDto.Response::from)
                .toList();
    }

    public ClientDto.Response findById(Long id) {
        return ClientDto.Response.from(getClient(id));
    }

    /* ---------- Customer space: an account can hold several profiles ---------- */

    public List<ClientDto.Response> findProfils(String username) {
        return clientRepository.findByUsernameOrderByNomAscPrenomAsc(username).stream()
                .map(ClientDto.Response::from)
                .toList();
    }

    @Transactional
    public ClientDto.Response creerProfil(String username, ClientDto.Request request) {
        return ClientDto.Response.from(creerPourCompte(username, request));
    }

    /** Also used by ReservationService when a profile is entered in the rental form. */
    @Transactional
    public Client creerPourCompte(String username, ClientDto.Request request) {
        String email = normaliserEmail(request.email());
        String permis = normaliserPermis(request.numPermis());
        verifierUnicite(email, permis, null);
        Client client = new Client();
        appliquer(client, request, email, permis);
        client.setUsername(username);
        client.setDateInscription(LocalDate.now());
        return clientRepository.save(client);
    }

    @Transactional
    public ClientDto.Response modifierProfil(String username, Long id, ClientDto.Request request) {
        Client client = getProfil(username, id);
        String email = normaliserEmail(request.email());
        String permis = normaliserPermis(request.numPermis());
        verifierUnicite(email, permis, id);
        appliquer(client, request, email, permis);
        return ClientDto.Response.from(client);
    }

    @Transactional
    public void supprimerProfil(String username, Long id) {
        Client client = getProfil(username, id);
        if (!client.getReservations().isEmpty()) {
            throw new BusinessRuleException("Ce profil a des réservations : il ne peut pas être supprimé.");
        }
        clientRepository.delete(client);
    }

    /** A profile of another account answers 404, as if it did not exist. */
    public Client getProfil(String username, Long id) {
        return clientRepository.findByIdClientAndUsername(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Profil", id));
    }

    /* ---------- Agency side ---------- */

    @Transactional
    public ClientDto.Response create(ClientDto.Request request) {
        String email = normaliserEmail(request.email());
        String permis = normaliserPermis(request.numPermis());
        verifierUnicite(email, permis, null);
        Client client = new Client();
        appliquer(client, request, email, permis);
        client.setDateInscription(LocalDate.now());
        return ClientDto.Response.from(clientRepository.save(client));
    }

    @Transactional
    public ClientDto.Response update(Long id, ClientDto.Request request) {
        Client client = getClient(id);
        String email = normaliserEmail(request.email());
        String permis = normaliserPermis(request.numPermis());
        verifierUnicite(email, permis, id);
        appliquer(client, request, email, permis);
        return ClientDto.Response.from(client);
    }

    @Transactional
    public void delete(Long id) {
        Client client = getClient(id);
        if (!client.getReservations().isEmpty()) {
            throw new BusinessRuleException("Ce client a des réservations : il ne peut pas être supprimé.");
        }
        clientRepository.delete(client);
    }

    private Client getClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
    }

    /** idClient is null for a new client. */
    private void verifierUnicite(String email, String permis, Long idClient) {
        boolean emailPris = idClient == null
                ? clientRepository.existsByEmail(email)
                : clientRepository.existsByEmailAndIdClientNot(email, idClient);
        if (emailPris) {
            throw new BusinessRuleException("Un client utilise déjà l'email " + email + ".");
        }
        boolean permisPris = idClient == null
                ? clientRepository.existsByNumPermis(permis)
                : clientRepository.existsByNumPermisAndIdClientNot(permis, idClient);
        if (permisPris) {
            throw new BusinessRuleException("Un client utilise déjà le permis " + permis + ".");
        }
    }

    private static void appliquer(Client client, ClientDto.Request request, String email, String permis) {
        client.setNom(request.nom().trim());
        client.setPrenom(request.prenom().trim());
        client.setEmail(email);
        client.setTelephone(request.telephone());
        client.setNumPermis(permis);
    }

    private static String normaliserEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normaliserPermis(String permis) {
        return permis.trim().toUpperCase(Locale.ROOT);
    }
}
