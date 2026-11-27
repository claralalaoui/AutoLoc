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

    /** Profile of the customer linked to a login (customer space). */
    public ClientDto.Response findProfil(String username) {
        return clientRepository.findByUsername(username)
                .map(ClientDto.Response::from)
                .orElseThrow(() -> new ResourceNotFoundException("Profil client non renseigné."));
    }

    /** Creates the customer profile at the first call, updates it afterwards. */
    @Transactional
    public ClientDto.Response enregistrerProfil(String username, ClientDto.Request request) {
        Client client = clientRepository.findByUsername(username).orElseGet(() -> {
            Client nouveau = new Client();
            nouveau.setUsername(username);
            nouveau.setDateInscription(LocalDate.now());
            return nouveau;
        });
        String email = normaliserEmail(request.email());
        String permis = request.numPermis().trim();
        verifierUnicite(email, permis, client.getIdClient());
        appliquer(client, request, email, permis);
        return ClientDto.Response.from(clientRepository.save(client));
    }

    @Transactional
    public ClientDto.Response create(ClientDto.Request request) {
        String email = normaliserEmail(request.email());
        String permis = request.numPermis().trim();
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
        String permis = request.numPermis().trim();
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
}
