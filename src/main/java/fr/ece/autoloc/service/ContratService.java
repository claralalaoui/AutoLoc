package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.web.dto.ContratDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContratService {

    private final ContratRepository contratRepository;

    public List<ContratDto.Response> findAll() {
        return contratRepository.findAllByOrderByDateSignatureDesc().stream()
                .map(ContratDto.Response::from)
                .toList();
    }

    public ContratDto.Response findById(Long id) {
        return ContratDto.Response.from(getContrat(id));
    }

    @Transactional
    public ContratDto.Response ajouterPaiement(Long id, ContratDto.PaiementRequest request) {
        Contrat contrat = getContrat(id);
        if (!contrat.isValide()) {
            throw new BusinessRuleException("Ce contrat n'est plus valide.");
        }
        ContratDto.Response avant = ContratDto.Response.from(contrat);
        if (request.montant().compareTo(avant.resteAPayer()) > 0) {
            throw new BusinessRuleException("Le paiement dépasse le reste à payer (" + avant.resteAPayer() + " €).");
        }

        Paiement paiement = new Paiement();
        paiement.setMontant(request.montant());
        paiement.setModePaiement(request.modePaiement());
        paiement.setDatePaiement(LocalDate.now());
        contrat.addPaiement(paiement);   // CascadeType.ALL persists the new payment with its contract
        contratRepository.flush();       // generate the payment id before building the response
        return ContratDto.Response.from(contrat);
    }

    private Contrat getContrat(Long id) {
        return contratRepository.findWithDetailsByIdContrat(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat", id));
    }
}
